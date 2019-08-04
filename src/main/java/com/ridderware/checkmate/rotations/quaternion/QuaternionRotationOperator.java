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
import org.apache.logging.log4j.*;

/**
 * A quaternion rotation operator is a special quaternion that has properties of rotating
 * vectors in fixed frames, or frames relative to fixed vectors.  A quaternion rotation operator
 * must have a norm of 1 (i.e., be a unit quaternion), and operates on a vector which can be
 * represented as a pure quaternion (vector in R3).  This class provides rotation operators that require
 * these two conditions to be true.
 *
 * @author Jeff Ridder
 */
public class QuaternionRotationOperator extends Quaternion implements IRotationOperator
{
    private static Logger logger =
        LogManager.getLogger(QuaternionRotationOperator.class);

    /** Creates a new instance of QuaternionRotationOperator 
     * @param q0 scalar part.
     * @param q1 i part.
     * @param q2 j part.
     * @param q3 k part.
     */
    public QuaternionRotationOperator(double q0, double q1, double q2,
        double q3)
    {
        super(q0, q1, q2, q3);

        double norm = this.norm();

        if (norm < 1. - 1e8 || norm > 1. + 1e8)
        {
            logger.error("Rotation operators must be unit quaternions!  Not a unit quaternion: norm = " +
                norm);
        }

    }

    /**
     * Creates a new instance of QuaternionRotationOperator that is a duplicate of q.
     * @param q the quaternion to be duplicated.
     */
    public QuaternionRotationOperator(Quaternion q)
    {
        this(q.q0, q.q1, q.q2, q.q3);
    }

    /**
     * Returns the input vector rotated in a fixed frame.
     * @param v vector to be rotated.
     * @return the rotated vector.
     */
    @Override
    public final double[] rotateVector(double[] v)
    {
        if (v.length != 3)
        {
            logger.error("Rotation operators operate on pure quaterions (vector in R3). Not operating on a pure quaternion: length = " +
                v.length);
        }

        final double[] w = new double[3];

        final double p = 2 * q0 * q0 - 1;

        final double Q11 = p + 2 * q1 * q1;

        final double Q12 = 2 * q1 * q2 - 2 * q0 * q3;

        final double Q13 = 2 * q1 * q3 + 2 * q0 * q2;

        final double Q21 = Q12 + 4 * q0 * q3;

        final double Q22 = p + 2 * q2 * q2;

        final double Q23 = 2 * q2 * q3 - 2 * q0 * q1;

        final double Q31 = Q13 - 4 * q0 * q2;

        final double Q32 = Q23 + 4 * q0 * q1;

        final double Q33 = p + 2 * q3 * q3;

        w[0] = Q11 * v[0] + Q12 * v[1] + Q13 * v[2];

        w[1] = Q21 * v[0] + Q22 * v[1] + Q23 * v[2];

        w[2] = Q31 * v[0] + Q32 * v[1] + Q33 * v[2];

        return w;
    }

    /**
     * Rotates the frame about the input vector.
     * @param v vector about which the frame is rotated.
     * @return the vector in the rotated frame.
     */
    @Override
    public final double[] rotateFrame(double[] v)
    {
        if (v.length != 3)
        {
            logger.error("Rotation operators operate on pure quaterions (vector in R3). Not operating on a pure quaternion: length = " +
                v.length);
        }

        final double[] w = new double[3];

        final double p = 2 * q0 * q0 - 1;

        final double Q11 = p + 2 * q1 * q1;

        final double Q12 = 2 * q1 * q2 + 2 * q0 * q3;

        final double Q13 = 2 * q1 * q3 - 2 * q0 * q2;

        final double Q21 = Q12 - 4 * q0 * q3;

        final double Q22 = p + 2 * q2 * q2;

        final double Q23 = 2 * q2 * q3 + 2 * q0 * q1;

        final double Q31 = Q13 + 4 * q0 * q2;

        final double Q32 = Q23 - 4 * q0 * q1;

        final double Q33 = p + 2 * q3 * q3;

        w[0] = Q11 * v[0] + Q12 * v[1] + Q13 * v[2];

        w[1] = Q21 * v[0] + Q22 * v[1] + Q23 * v[2];

        w[2] = Q31 * v[0] + Q32 * v[1] + Q33 * v[2];

        return w;
    }
}
