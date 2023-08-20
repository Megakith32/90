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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

class ZiplineIrGenerationExtension(
  private val messageCollector: MessageCollector,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val ziplineApis = ZiplineApis(pluginContext)

    val transformer = object : IrElementTransformerVoidWithContext() {
      override fun visitCall(expression: IrCall): IrExpression {
        val expression = super.visitCall(expression) as IrCall

        try {
          val newOutboundFunction = ziplineApis.outboundRewriteFunctions[expression.symbol]
          if (newOutboundFunction != null) {
            return OutboundBridgeRewriter(
              pluginContext,
              ziplineApis,
              currentScope!!,
              currentDeclarationParent!!,
              expression,
              newOutboundFunction,
            ).rewrite()
          }

          val newInboundFunction = ziplineApis.inboundRewriteFunctions[expression.symbol]
          if (newInboundFunction != null) {
            return InboundBridgeRewriter(
              pluginContext,
              ziplineApis,
              currentScope!!,
              currentDeclarationParent!!,
              expression,
              newInboundFunction,
            ).rewrite()
          }

          return expression
        } catch (e: ZiplineCompilationException) {
          messageCollector.report(e.severity, e.message, currentFile.locationOf(e.element))
          return expression
        }
      }
    }

    val transform = moduleFragment.transform(transformer, null)
//    println(transform.dump())
  }
}
