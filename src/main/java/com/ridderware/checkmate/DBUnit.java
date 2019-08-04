/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2007 Jeff Ridder
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
package com.ridderware.checkmate;

/**
 * Provides static methods to convert values to and from deciBels.
 *
 * @author Jeff Ridder
 */
public class DBUnit
{
    /**
     * Returns the input value as deciBels -- 10 x Log10(value).
     * @param value to be converted to dB.
     * @return value in dB.
     */
    public static double dB(double value)
    {
        return 10. * Math.log10(value);
    }

    /**
     * Returns the input value (in deciBels) as an absolute value -- 10^(value/10).
     * @param value in deciBels to be converted to absolute.
     * @return inverse of the dB value.
     */
    public static double idB(double value)
    {
        return Math.pow(10., value / 10.);
    }
}
