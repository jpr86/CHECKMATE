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
package com.ridderware.checkmate.rotations;

/**
 * Interface for rotation operators.
 *
 * @author Jeff Ridder
 */
public interface IRotationOperator
{
    /**
     * Returns the input vector rotated in a fixed frame.
     * @param v vector to be rotated.
     * @return the rotated vector.
     */
    public double[] rotateVector(double[] v);

    /**
     * Rotates the frame about the input vector.
     * @param v vector about which the frame is rotated.
     * @return the vector in the rotated frame.
     */
    public double[] rotateFrame(double[] v);
}
