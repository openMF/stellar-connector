/**
 * Copyright 2016 Myrle Krantz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mifos.module.stellar;

import java.util.LinkedList;
import java.util.List;

public class Cleanup implements AutoCloseable {
  @Override public void close() throws Exception {
    cleanup();
  }

  public interface Step {
    void clean() throws Exception;
  }

  private List<Step> steps = new LinkedList<>();

  public Cleanup() { super(); }

  public void addStep (final Step newFirstStep) { steps.add(0, newFirstStep); }

  public void cleanup() throws Exception
  {
    for (final Step step : steps)
    {
      step.clean();
    }
  }
}
