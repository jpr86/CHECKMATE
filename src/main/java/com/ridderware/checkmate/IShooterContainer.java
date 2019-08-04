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
 * Interface declaring methods common to shooters.
 *
 * @author Jeff Ridder
 */
public interface IShooterContainer
{
    /**
     * Returns a figure of merit that assists upper echelon in determining
     * the ability of this shooter to engage the target.  Engageability is defined as 
     * the ratio of target range to lethal range of the shooter (i.e., a target with
     * engageability < 1. is within lethal range of the shooter).
     * @param target the target platform for which engageability is to be determined.
     * @return the engageability figure-of-merit.
     */
    public double getEngageability(Platform target);

    /**
     * Assigns the target to the shooter.  This is a step before
     * engagement command that results in allocating the shooter.
     * The shooter will then have autonomy as to how to engage the target.
     *@param target target.
     */
    public void assignTarget(Platform target);

    /**
     * Called by the superior to assign a higher priority target to the shooter.  
     * If the shooter has no capacity, it will displace a current target to accept this one.
     * @param target priority target being assigned.
     */
    public void priorityAssignTarget(Platform target);

    /**
     * Unassigns the target from the shooter.  
     *@param target target.
     */
    public void unassignTarget(Platform target);

    /**
     * Called by the shooter to report whether the target was destroyed.
     * @param target the target that was engaged.
     * @param destroyed true if destroyed, false otherwise.
     */
    public void targetDestroyed(Platform target, boolean destroyed);
}
