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
 * An enum that performs angle units conversions.
 *
 * @author Jeff Ridder
 */
public enum AngleUnit
{
    /** Degrees-Minutes-Seconds as DD.MMSS */
    DMS(10.),
    /** Decimal Degrees */
    DD(1.),
    /** Radians */
    RADIANS(180. / Math.PI);

    private final double toDDConversionFactor;

    private AngleUnit(double toDDConversionFactor)
    {
        this.toDDConversionFactor = toDDConversionFactor;
    }

    /**
     * Converts the given angle in the given unit to this unit. Conversions
     * that exceed Double.MaxValue are returned at Double.
     * Conversions that would go below double.MinValue are capped at double.MinValue.
     * 
     * @param angle the angle to convert
     * @param units the unit you are converting to
     * @return value in the converted units
     */
    public double convert(double angle, AngleUnit units)
    {
        double conversion = angle;

        //  First, convert this to DD
        if (this.toDDConversionFactor == AngleUnit.DMS.toDDConversionFactor)
        {
            conversion = dmsToDeg(angle);
        }
        else
        {
            conversion = angle * this.toDDConversionFactor;
        }

        //  Now convert the DD to the desired units
        if (units.toDDConversionFactor == AngleUnit.DMS.toDDConversionFactor)
        {
            conversion = degToDMS(conversion);
        }
        else
        {
            conversion /= units.toDDConversionFactor;
        }

        return conversion;
    }

    /**
     *  Converts DD to DMS (degrees-minutes-seconds) format.
     *
     * @param  degrees  DD.
     * @return          DMS.
     */
    private double degToDMS(double degrees)
    {
        int sign;
        double deg;
        double mag;
        double d;
        double m;
        double s;

        mag = Math.abs(degrees);

        if (degrees != 0.)
        {
            sign = (int) (mag / degrees);
        }
        else
        {
            sign = 1;
        }

        d = Math.floor(mag / 0.999999);
        m = Math.max(Math.floor((mag - d) * 60. / 0.999999) / 100., 0.);
        s = (((mag - d) * 60.) - m * 100.) * 60. / 10000.;

        deg = d + m + s;

        return (deg * sign);
    }

    /**
     *  Converts DMS to DD.
     *
     * @param  dms  degrees-minutes-seconds.
     * @return      decimal degrees.
     */
    private double dmsToDeg(double dms)
    {
        int sign;
        double ts;
        double deg;
        double mag;
        double d;
        double m;
        double s;

        mag = Math.abs(dms);

        if (dms != 0.)
        {
            sign = (int) (mag / dms);
        }
        else
        {
            sign = 1;
        }

        d = Math.floor(mag);
        m = Math.floor((mag - d) * 100.00001);
        s = Math.floor((mag - d - m / 100.) * 10000.00001);
        ts = Math.abs(mag - d - m / 100. - s / 10000.) * 1000000.00001;
        deg = d + m / 60. + (s + ts / 100.) / 3600.;

        return (deg * sign);
    }

    /**
     *  Normalizes any input angle to its equivalent between the base and base+2*PI.
     *
     * @param  angle  angle in radians
     * @param  base   base for normalization in radians
     * @return        normalized angle.
     */
    public static double normalizeAngle(double angle, double base)
    {
        double two_pi = 2. * Math.PI;

        double upper = base + two_pi;

        while (angle < base)
        {
            angle += two_pi;
        }

        while (angle > upper)
        {
            angle -= two_pi;
        }

        return angle;
    }

    /**
     *  Normalizes any arc angle specified by the two input angles to be between
     *  -PI and +PI.
     *
     * @param  a1  starting angle (radians).
     * @param  a2  ending angle (radians).
     * @return         normalized arc angle (radians).
     */
    public static double normalizeArc(double a1, double a2)
    {
        double two_pi = 2. * Math.PI;

        double arc = Math.abs(a2 - a1);

        double sign = 1.;
        if (arc > 0.)
        {
            sign = (a2 - a1) / arc;
        }

        while (arc > Math.PI)
        {
            arc -= two_pi;
        }

        return (sign * arc);
    }
}
