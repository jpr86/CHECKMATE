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

import com.ridderware.checkmate.rotations.IRotationOperator;

/**
 * A rotation operator using an embedded quaternion to implement the Aerospace Euler Rotation Sequence.
 * This is a rotation about the z-axis (heading), followed by y (elevation), and x last (bank).
 *
 * @author Jeff Ridder
 */
public class AerospaceSequenceQuaternion implements IRotationOperator
{
    private final double heading;

    private final double elevation;

    private final double bank;

    private final QuaternionRotationOperator qr;

    /** Creates a new instance of AerospaceSequenceQuaternion
     * @param heading heading angle relative to the reference frame.
     * @param elevation elevation angle relative to the reference frame.
     * @param bank bank angle relative to the reference frame.
     */
    public AerospaceSequenceQuaternion(double heading, double elevation,
        double bank)
    {
        this.heading = heading;
        this.elevation = elevation;
        this.bank = bank;

        double a = heading / 2.;
        double b = elevation / 2.;
        double g = bank / 2.;

        double sa = Math.sin(a);
        double ca = Math.cos(a);
        double sb = Math.sin(b);
        double cb = Math.cos(b);
        double sg = Math.sin(g);
        double cg = Math.cos(g);

        double q0 = ca * cb * cg + sa * sb * sg;
        double q1 = ca * cb * sg - sa * sb * cg;
        double q2 = ca * sb * cg + sa * cb * sg;
        double q3 = sa * cb * cg - ca * sb * sg;

        this.qr = new QuaternionRotationOperator(q0, q1, q2, q3);
    }

    /**
     * Creates a new instance of AerospaceSequenceQuaternion from the input orientation vector.
     * @param orientation the orientation vector as az, el, and bank angles.
     */
    public AerospaceSequenceQuaternion(double[] orientation)
    {
        this(orientation[0], orientation[1], orientation[2]);
    }

    /**
     * Returns the input vector rotated in a fixed frame.
     * @param v vector to be rotated.
     * @return the rotated vector.
     */
    @Override
    public double[] rotateVector(double[] v)
    {
        return qr.rotateVector(v);
    }

    /**
     * Rotates the frame about the input vector.
     * @param v vector about which the frame is rotated.
     * @return the vector in the rotated frame.
     */
    @Override
    public double[] rotateFrame(double[] v)
    {
        return qr.rotateFrame(v);
    }

    /**
     * Returns the embedded quaternion.
     * @return quaternion rotation operator.
     */
    public Quaternion getQuaternion()
    {
        return this.qr;
    }

    /**
     * Returns the heading rotation angle corresponding to this rotation sequence.
     * @return heading angle.
     */
    public final double getHeading()
    {
        return heading;
    }

    /**
     * Returns the elevation rotation angle corresponding to this rotation sequence.
     * @return elevation angle.
     */
    public final double getElevation()
    {
        return elevation;
    }

    /**
     * Returns the bank rotation angle corresponding to this rotation sequence.
     * @return bank angle.
     */
    public double getBank()
    {
        return bank;
    }
}
