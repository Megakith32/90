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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * A user-defined interface (like `EchoService` or `Callback<String>`) and support for either
 * implementing it ([OutboundBridgeRewriter]) or calling it ([InboundBridgeRewriter]).
 *
 * This class tracks the interface type (like `EchoService` or `Callback<String>`) and its
 * implementation class (that doesn't know its generic parameters).
 *
 * This class can declare a `KSerializer` property for each unique parameter type and return type on
 * the interface. Use [declareSerializerProperties] to create these, then [serializerExpression] to
 * access a specific serializer. Declaring serializers is useful to fast fail if ever a serializer
 * is required but not configured.
 */
internal class BridgedInterface(
  private val pluginContext: IrPluginContext,
  private val ziplineApis: ZiplineApis,

  /** A specific type identifier that knows the values of its generic parameters. */
  val type: IrType,

  /** A potentially-generic declaration that doesn't have values for its generic parameters. */
  private val classSymbol: IrClassSymbol,
) {
  /** Maps types to the property holding the corresponding serializer. */
  private val typeToSerializerProperty = mutableMapOf<IrType, IrProperty>()

  val bridgedFunctions: List<IrSimpleFunctionSymbol>
    // TODO(jwilson): find a better way to skip equals()/hashCode()/toString()
    get() = classSymbol.functions.toList()
      .filterNot { it.owner.isFakeOverride }

  /** Declares properties for all the serializers needed to bridge this interface. */
  fun declareSerializerProperties(
    declaringClass: IrClass,
    contextParameter: IrValueParameter,
  ) {
    check(typeToSerializerProperty.isEmpty()) { "declareSerializerProperties() called twice?" }

    val serializedTypes = mutableSetOf<IrType>()
    for (bridgedFunction in bridgedFunctions) {
      for (valueParameter in bridgedFunction.owner.valueParameters) {
        serializedTypes += resolveTypeParameters(valueParameter.type)
      }
      serializedTypes += resolveTypeParameters(bridgedFunction.owner.returnType)
    }

    for (serializedType in serializedTypes) {
      val serializerProperty = irSerializerProperty(
        declaringClass = declaringClass,
        contextParameter = contextParameter,
        type = serializedType,
        name = Name.identifier("serializer_${typeToSerializerProperty.size}")
      )
      declaringClass.declarations += serializerProperty
      typeToSerializerProperty[serializedType] = serializerProperty
    }
  }

  private fun irSerializerProperty(
    declaringClass: IrClass,
    contextParameter: IrValueParameter,
    type: IrType,
    name: Name
  ): IrProperty {
    val serializersModuleProperty = when (contextParameter.type.classFqName) {
      ziplineApis.outboundBridgeContextFqName -> ziplineApis.outboundBridgeContextSerializersModule
      ziplineApis.inboundBridgeContextFqName -> ziplineApis.inboundBridgeContextSerializersModule
      else -> error("unexpected Context type")
    }

    // val serializer_0: KSerializer<EchoRequest> = context.serializersModule.serializer<EchoRequest>()
    val kSerializerOfT = ziplineApis.kSerializer.typeWith(type)
    return irVal(
      pluginContext = pluginContext,
      propertyType = kSerializerOfT,
      declaringClass = declaringClass,
      propertyName = name,
    ) {
      irExprBody(
        irCall(
          callee = ziplineApis.serializerFunction,
          type = kSerializerOfT,
        ).apply {
          putTypeArgument(0, type)
          extensionReceiver = irCall(
            callee = serializersModuleProperty.owner.getter!!,
          ).apply {
            dispatchReceiver = irGet(contextParameter)
          }
        })
    }
  }

  /** Call this on any declaration returned by [classSymbol] to fill in the generic parameters. */
  fun resolveTypeParameters(type: IrType): IrType {
    val simpleType = this.type as? IrSimpleType ?: return type
    val parameters = classSymbol.owner.typeParameters
    val arguments = simpleType.arguments.map { it as IrType }
    return type.substitute(parameters, arguments)
  }

  /** Returns an expression that returns the requested serializer. */
  fun serializerExpression(
    irBuilder: IrBuilderWithScope,
    serializerType: IrType,
    declaringInstance: IrValueParameter,
  ): IrFunctionAccessExpression {
    val property = typeToSerializerProperty[serializerType]!!
    return irBuilder.irCall(
      callee = property.getter!!
    ).apply {
      dispatchReceiver = irBuilder.irGet(declaringInstance)
    }
  }

  companion object {
    fun create(
      pluginContext: IrPluginContext,
      ziplineApis: ZiplineApis,
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

      return BridgedInterface(pluginContext, ziplineApis, type, classSymbol)
    }
  }
}
