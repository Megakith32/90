/*
 * Copyright (C) 2019 Square, Inc.
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
#include <jni.h>
#include <new>
#include "Context.h"
#include "InboundCallChannel.h"
#include "ExceptionThrowers.h"

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_zipline_QuickJs_createContext(JNIEnv* env, jclass type) {
  Context* c = new(std::nothrow) Context(env);
  if (!c || !c->jsContext || !c->jsRuntime) {
    delete c;
    c = nullptr;
  }
  return reinterpret_cast<jlong>(c);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_destroyContext(JNIEnv* env, jobject type, jlong context) {
  delete reinterpret_cast<Context*>(context);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv* env,
                                                                                    jobject type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode,
                                                                                    jstring fileName) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->eval(env, sourceCode, fileName);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_zipline_QuickJs_getInboundCallChannel(JNIEnv* env, jobject thiz, jlong _context, jstring name) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return 0L;
  }

  return reinterpret_cast<jlong>(context->getInboundCallChannel(env, name));
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_setOutboundCallChannel(JNIEnv* env, jobject thiz, jlong _context,
                                             jstring name, jobject callChannel) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setOutboundCallChannel(env, name, callChannel);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_QuickJs_execute(JNIEnv* env, jobject thiz, jlong _context, jbyteArray bytecode) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException", "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->execute(env, bytecode);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_cash_zipline_QuickJs_compile(JNIEnv* env, jobject thiz, jlong _context, jstring sourceCode, jstring fileName) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException", "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->compile(env, sourceCode, fileName);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_setInterruptHandler(JNIEnv* env, jobject type, jlong context_, jobject interruptHandler) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setInterruptHandler(env, interruptHandler);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_QuickJs_memoryUsage(JNIEnv* env, jobject type, jlong context_) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->memoryUsage(env);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_setMemoryLimit(JNIEnv* env, jobject type, jlong context_, jlong limit) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setMemoryLimit(env, limit);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_setGcThreshold(JNIEnv* env, jobject type, jlong context_, jlong gcThreshold) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setGcThreshold(env, gcThreshold);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_QuickJs_setMaxStackSize(JNIEnv* env, jobject type, jlong context_, jlong stackSize) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setMaxStackSize(env, stackSize);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_cash_zipline_JniCallChannel_serviceNamesArray(JNIEnv* env, jobject thiz, jlong _context,
                                                       jlong instance) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }

  const InboundCallChannel* channel = reinterpret_cast<const InboundCallChannel*>(instance);
  if (!channel) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return nullptr;
  }

  return channel->serviceNamesArray(context, env);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_cash_zipline_JniCallChannel_invoke(JNIEnv* env, jobject thiz, jlong _context,
                                            jlong instance, jstring instanceName, jstring funName,
                                            jobjectArray encodedArguments) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }

  const InboundCallChannel* channel = reinterpret_cast<const InboundCallChannel*>(instance);
  if (!channel) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return nullptr;
  }

  return channel->invoke(context, env, instanceName, funName, encodedArguments);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_JniCallChannel_invokeSuspending(JNIEnv* env, jobject thiz, jlong _context,
                                                      jlong instance, jstring instanceName,
                                                      jstring funName, jobjectArray encodedArguments,
                                                      jstring callbackName) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }

  const InboundCallChannel* channel = reinterpret_cast<const InboundCallChannel*>(instance);
  if (!channel) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return;
  }

  channel->invokeSuspending(context, env, instanceName, funName, encodedArguments, callbackName);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_JniCallChannel_disconnect(JNIEnv* env, jobject thiz, jlong _context,
                                                jlong instance, jstring instanceName) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return JNI_FALSE;
  }

  const InboundCallChannel* channel = reinterpret_cast<const InboundCallChannel*>(instance);
  if (!channel) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return JNI_FALSE;
  }

  return channel->disconnect(context, env, instanceName);
}
