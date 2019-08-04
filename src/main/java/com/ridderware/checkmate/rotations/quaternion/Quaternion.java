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
package com.ridderware.checkmate.rotations.quaternion;

/**
 *  Data type for quaternions.  This was lifted off the internet, but confirmed to be correct.
 *
 *  http://mathworld.wolfram.com/Quaternion.html
 *
 *  The data type is "immutable" so once you create and initialize
 *  a Quaternion, you cannot change it.
 */
public class Quaternion
{
    protected final double q0,  q1,  q2,  q3;

    /**
     * Creates a new quaternion with the specified components.
     * @param q0 scalar part.
     * @param q1 i part.
     * @param q2 j part.
     * @param q3 k part.
     */
    public Quaternion(double q0, double q1, double q2, double q3)
    {
        this.q0 = q0;
        this.q1 = q1;
        this.q2 = q2;
        this.q3 = q3;
    }

    /**
     * Returns a string representation of this quaternion.
     * @return the quaternion as a string.
     */
    @Override
    public final String toString()
    {
        return q0 + " + " + q1 + "i + " + q2 + "j + " + q3 + "k";
    }

    /**
     * Returns the norm of the quaternion.
     * @return norm.
     */
    public final double norm()
    {
        return Math.sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3);
    }

    /**
     * Returns the conjugate of the quaternion.
     * @return conjugate.
     */
    public final Quaternion conjugate()
    {
        return new Quaternion(q0, -q1, -q2, -q3);
    }

    /**
     * Returns the sum of this + b.
     * @param b quaternion to be summed with this.
     * @return the quaternion sum.
     */
    public final Quaternion plus(Quaternion b)
    {
        Quaternion a = this;
        return new Quaternion(a.q0 + b.q0, a.q1 + b.q1, a.q2 + b.q2, a.q3 + b.q3);
    }

    /**
     * Returns the product of this * b.
     * @param b the quaternion to be multiplied by this.
     * @return the product of this * b.
     */
    public final Quaternion times(Quaternion b)
    {
        Quaternion a = this;
        double y0 = a.q0 * b.q0 - a.q1 * b.q1 - a.q2 * b.q2 - a.q3 * b.q3;
        double y1 = a.q0 * b.q1 + a.q1 * b.q0 + a.q2 * b.q3 - a.q3 * b.q2;
        double y2 = a.q0 * b.q2 - a.q1 * b.q3 + a.q2 * b.q0 + a.q3 * b.q1;
        double y3 = a.q0 * b.q3 + a.q1 * b.q2 - a.q2 * b.q1 + a.q3 * b.q0;
        return new Quaternion(y0, y1, y2, y3);
    }

    /**
     * Returns the inverse of this quaternion.
     * @return the inverse.
     */
    public final Quaternion inverse()
    {
        double d = q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3;
        return new Quaternion(q0 / d, -q1 / d, -q2 / d, -q3 / d);
    }

    /**
     * Returns this divided by b.
     * @param b the quaternion to divide by.
     * @return this / b
     */
    public final Quaternion divides(Quaternion b)
    {
        Quaternion a = this;
        return a.inverse().times(b);
    }
}
