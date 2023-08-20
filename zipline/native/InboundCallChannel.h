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
#ifndef QUICKJS_ANDROID_INBOUNDCALLCHANNEL_H
#define QUICKJS_ANDROID_INBOUNDCALLCHANNEL_H

#include <jni.h>
#include <vector>
#include <string>
#include "quickjs/quickjs.h"

class Context;

class InboundCallChannel {
public:
  InboundCallChannel(JSContext *jsContext, const char *name);
  ~InboundCallChannel();

  jobjectArray serviceNamesArray(Context* context, JNIEnv*) const;
  jstring call(Context *context, JNIEnv* env, jstring callJson) const;
  jboolean disconnect(Context *context, JNIEnv* env, jstring instanceName) const;

  JSContext *jsContext;
  JSAtom nameAtom;
};

#endif //QUICKJS_ANDROID_INBOUNDCALLCHANNEL_H
