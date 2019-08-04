/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2006 Jeff Ridder
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
 * An enum that performs length units conversions.  If units that you would like
 * to convert to/from are not in this class, please add them or ask me to
 * add them.
 *
 * Author: Jason C. HandUber
 * Unit Conversion Factor Source: http://www.engineeringtoolbox.com/length-units-converter-d_1033.html
 * Partial Verification Source: http://www.sengpielaudio.com/calculator-millimeter.htm
 */
public enum LengthUnit
{
    PICOMETERS(Math.pow(10, -12)),
    ANGSTROMES(Math.pow(10, -10)),
    NANOMETERS(Math.pow(10, -9)),
    MICROMETERS(Math.pow(10, -6)),
    MILLIMETERS(Math.pow(10, -3)),
    CENTIMETERS(Math.pow(10, -2)),
    DECIMETERS(Math.pow(10, -1)),
    METERS(Math.pow(10, 0)),
    HECTOMETERS(Math.pow(10, 2)),
    KILOMETERS(Math.pow(10, 3)),
    MEGAMETERS(Math.pow(10, 6)),
    GIGAMETERS(Math.pow(10, 9)),
    INCHES(0.0254),
    FEET(0.3048),
    YARDS(0.914),
    MILES(1610),
    NAUTICAL_MILES(1852);

    private final double toMetersConversionFactor;

    private LengthUnit(double toMetersConversionFactor)
    {
        this.toMetersConversionFactor = toMetersConversionFactor;
    }

    /**
     * Converts the given length in the given unit to this unit. Conversions
     * that exceed Double.MaxValue are returned at Double.
     * Conversions that would go below double.MinValue are capped at double.MinValue.
     * 
     * @param distance the distance to convert
     * @param units the unit you are converting to
     * @return value in the converted units
     */
    public double convert(double distance, LengthUnit units)
    {
        return this.toMetersConversionFactor * distance /
            units.toMetersConversionFactor;
    }
}
