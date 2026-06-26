/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.life.app.engine.agent;

import java.util.List;

/**
 * Structured output schema for the contractor quotes worker.
 *
 * @param quoteCount number of quotes gathered
 * @param quotes list of quote items from contractors
 */
public record GetQuotesResult(int quoteCount, List<QuoteItem> quotes) {

    /**
     * Individual quote from a contractor.
     *
     * @param contractor contractor name
     * @param amount quoted price
     * @param available whether contractor is available for the work
     */
    public record QuoteItem(String contractor, int amount, boolean available) {}
}
