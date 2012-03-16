// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package adaptorlib;

import java.util.Locale;

class TranslationStatus implements Status {
  private final Code code;
  private final Translation message;
  private final Object[] params;

  public TranslationStatus(Code code) {
    this(code, null);
  }

  public TranslationStatus(Code code, Translation message, Object... params) {
    if (code == null) {
      throw new NullPointerException("Code must not be null");
    }
    this.code = code;
    this.message = message;
    this.params = params;
  }

  @Override
  public Code getCode() {
    return code;
  }

  @Override
  public String getMessage(Locale locale) {
    return message == null ? null : message.toString(locale, params);
  }
}
