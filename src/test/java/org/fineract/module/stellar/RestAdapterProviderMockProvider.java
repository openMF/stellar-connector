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
package org.fineract.module.stellar;

import org.fineract.module.stellar.fineractadapter.Adapter;
import org.fineract.module.stellar.fineractadapter.FineractClientService;
import org.fineract.module.stellar.fineractadapter.JournalEntryCommand;
import org.fineract.module.stellar.fineractadapter.RestAdapterProvider;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.jmock.lib.legacy.ClassImposteriser;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit.RestAdapter;

public class RestAdapterProviderMockProvider {
  static public RestAdapterProvider getRestAdapterProviderMock(
      final Mockery context,
      final String mifosAddress)
  {
    final RestAdapterProvider restAdapterProviderMock = context.mock(RestAdapterProvider.class);
    final RestAdapter restAdapterMock = context.mock(RestAdapter.class);
    final FineractClientService fineractClientServiceMock = context.mock(FineractClientService.class);

    context.checking( new Expectations() {{
      allowing(restAdapterProviderMock).get(mifosAddress);
      will(returnValue(restAdapterMock));

      allowing(restAdapterMock).create(FineractClientService.class);
      will(returnValue(fineractClientServiceMock));

      allowing(fineractClientServiceMock).createSavingsAccountTransaction(
          with(any(Integer.class)),
          with(any(String.class)),
          with(any(JournalEntryCommand.class)));
    }});

    return restAdapterProviderMock;
  }

  static public void mockFineract(final Adapter adapter, final String mifosAddress) {
    final Mockery context = new Mockery();
    context.setThreadingPolicy(new Synchroniser());
    context.setImposteriser(ClassImposteriser.INSTANCE);
    ReflectionTestUtils.setField(adapter, "restAdapterProvider",
        getRestAdapterProviderMock(context, mifosAddress));
  }
}
