/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.kotlin

import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * A user-defined interface (like `EchoService` or `Callback<String>`) and support for generating
 * its `ZiplineServiceAdapter`.
 *
 * This class tracks the interface type (like `EchoService` or `Callback<String>`) and its
 * implementation class (that doesn't know its generic parameters).
 *
 * This class can declare a `KSerializer` property for each unique parameter type and return type on
 * the interface. Use [declareSerializerTemporaries] to create these, then the resulting elements
 * access a specific serializer. Declaring serializers is useful to fast fail if ever a serializer
 * is required but not configured.
 */
internal class BridgedInterface(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,
  private val scope: ScopeWithIr,

  /** A specific type identifier that knows the values of its generic parameters. */
  val type: IrType,

  /** A potentially-generic declaration that doesn't have values for its generic parameters. */
  private val classSymbol: IrClassSymbol,
) {
  val typeIrClass = classSymbol.owner

  // TODO(jwilson): support overloaded functions?
  val bridgedFunctionsWithOverrides: Map<Name, List<IrSimpleFunctionSymbol>>
    get() {
      val result = mutableMapOf<Name, MutableList<IrSimpleFunctionSymbol>>()
      for (supertype in listOf(classSymbol.owner.defaultType) + classSymbol.owner.superTypes) {
        val supertypeClass = supertype.getClass() ?: continue
        for (function in supertypeClass.functions) {
          if (function.name.identifier in NON_INTERFACE_FUNCTION_NAMES) continue
          val overrides = result.getOrPut(function.name) { mutableListOf() }
          overrides += function.symbol
        }
        for (property in supertypeClass.properties) {
          val getter = property.getter
          if (getter != null) {
            val overrides = result.getOrPut(getter.name) { mutableListOf() }
            overrides += getter.symbol
          }
          val setter = property.setter
          if (setter != null) {
            val overrides = result.getOrPut(setter.name) { mutableListOf() }
            overrides += setter.symbol
          }
        }
      }
      return result
    }

  val bridgedFunctions: List<IrSimpleFunctionSymbol>
    get() = bridgedFunctionsWithOverrides.values.map { it[0] }

  /** Declares local vars for all the serializers needed to bridge this interface. */
  fun declareSerializerTemporaries(
    statementsBuilder: IrStatementsBuilder<*>,
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
  ): Map<IrType, IrVariable> {
    return statementsBuilder.irDeclareSerializerTemporaries(
      serializersModuleParameter = serializersModuleParameter,
      serializersExpression = serializersExpression,
    )
  }

  private fun IrStatementsBuilder<*>.irDeclareSerializerTemporaries(
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
  ): Map<IrType, IrVariable> {
    val requiredTypes = mutableSetOf<IrType>()
    for (bridgedFunction in bridgedFunctions) {
      for (valueParameter in bridgedFunction.owner.valueParameters) {
        requiredTypes += resolveTypeParameters(valueParameter.type)
      }
      val resolvedReturnType = resolveTypeParameters(bridgedFunction.owner.returnType)
      requiredTypes += when {
        bridgedFunction.isSuspend -> ziplineApis.suspendCallback.typeWith(resolvedReturnType)
        else -> resolvedReturnType
      }
    }

    val result = mutableMapOf<IrType, IrVariable>()
    for (requiredType in requiredTypes) {
      val serializerExpression = serializerExpression(
        type = requiredType,
        serializersModuleParameter = serializersModuleParameter,
        serializersExpression = serializersExpression,
        contextual = requiredType.isContextual,
      )
      result[requiredType] = irTemporary(
        value = serializerExpression.expression,
        nameHint = "serializer",
        isMutable = false,
      )
    }
    return result
  }

  private val IrType.isContextual
    get() = annotations.any { it.type.classFqName == ziplineApis.contextualFqName }

  class SerializerExpression(
    val expression: IrExpression,
    val hasTypeParameter: Boolean,
  )

  /**
   * To serialize generic types that include type variables ('T'), we use [serializersExpression] to
   * extract the corresponding serializer from the list, matching by index.
   *
   * Whenever serializers for type parameters are returned they must be used! Otherwise, we will try
   * to look up a serializer for a type variable, and that will fail because the concrete type is
   * not known.
   */
  private fun IrBuilderWithScope.serializerExpression(
    type: IrType,
    serializersModuleParameter: IrValueParameter,
    serializersExpression: IrVariable,
    contextual: Boolean,
  ): SerializerExpression {
    val originalArguments = (type as IrSimpleType).arguments
    val resolvedArguments = originalArguments.map { resolveTypeParameters(it as IrType) }

    val parameterExpressions = resolvedArguments.map { argumentType ->
      serializerExpression(
        type = argumentType,
        serializersModuleParameter = serializersModuleParameter,
        serializersExpression = serializersExpression,
        contextual = argumentType.isContextual,
      )
    }
    val parameterList = irCall(ziplineApis.listOfFunction).apply {
      this.type = ziplineApis.listOfKSerializerStar
      putTypeArgument(0, ziplineApis.kSerializer.starProjectedType)
      putValueArgument(
        0,
        irVararg(
          ziplineApis.kSerializer.starProjectedType,
          parameterExpressions.map { it.expression },
        )
      )
    }

    val hasTypeParameter = parameterExpressions.any { it.hasTypeParameter }
    val classifierOwner = type.classifier.owner
    val isTypeParameter = classifierOwner is IrTypeParameter

    val expression = when {
      classifierOwner is IrTypeParameter -> {
        // serializers.get(0)
        irCall(
          callee = ziplineApis.listGetFunction,
          type = ziplineApis.kSerializer.starProjectedType,
        ).apply {
          dispatchReceiver = irGet(serializersExpression)
          putValueArgument(0, irInt(classifierOwner.index))
        }
      }

      type.isSubtypeOfClass(ziplineApis.ziplineService) -> {
        // ServiceType.Companion.Adapter(serializers)
        AdapterGenerator(
          pluginContext,
          ziplineApis,
          this@BridgedInterface.scope,
          pluginContext.referenceClass(type.classFqName!!)!!.owner,
        ).adapterExpression(parameterList)
      }

      hasTypeParameter || contextual || type.isFlow -> {
        // serializersModule.requireContextual<T>(root KClass, recurse on type args)
        irCall(
          callee = ziplineApis.requireContextual,
          type = ziplineApis.kSerializer.starProjectedType,
        ).apply {
          extensionReceiver = irGet(serializersModuleParameter)
          // TODO: call remapTypeParameters passing typeIrClass and the AdapterClass we're making
          putTypeArgument(0, type)
          putValueArgument(
            0,
            irKClass(pluginContext.referenceClass(type.classFqName!!)!!.owner)
          )
          putValueArgument(1, parameterList)
        }
      }

      else -> {
        // serializersModule.serializer<T>()
        irCall(
          callee = ziplineApis.serializerFunctionTypeParam,
          type = ziplineApis.kSerializer.starProjectedType,
        ).apply {
          // TODO: call remapTypeParameters passing typeIrClass and the AdapterClass we're making
          putTypeArgument(0, type)
          extensionReceiver = irGet(serializersModuleParameter)
        }
      }
    }

    return SerializerExpression(
      expression = expression,
      hasTypeParameter = hasTypeParameter || isTypeParameter,
    )
  }

  private val IrType.isFlow
    get() = classFqName == ziplineApis.flowFqName

  /** Call this on any declaration returned by [classSymbol] to fill in the generic parameters. */
  fun resolveTypeParameters(type: IrType): IrType {
    val simpleType = this.type as? IrSimpleType ?: return type
    val parameters = typeIrClass.typeParameters
    val arguments = simpleType.arguments.map { it as IrType }
    return type.substitute(parameters, arguments)
  }

  companion object {
    /** Don't bridge these. */
    private val NON_INTERFACE_FUNCTION_NAMES = mutableSetOf(
      "equals",
      "hashCode",
      "toString",
    )

    fun create(
      pluginContext: IrPluginContext,
      ziplineApis: ZiplineApis,
      scope: ScopeWithIr,
      element: IrElement,
      functionName: String,
      type: IrType,
    ): BridgedInterface {
      val classSymbol = pluginContext.referenceClass(type.classFqName ?: FqName.ROOT)
      if (classSymbol == null || !classSymbol.owner.isInterface) {
        throw ZiplineCompilationException(
          element = element,
          message = "The type argument to $functionName must be an interface type" +
            " (but was ${type.classFqName})",
        )
      }

      return BridgedInterface(
        pluginContext,
        ziplineApis,
        scope,
        type,
        classSymbol
      )
    }
  }
}
