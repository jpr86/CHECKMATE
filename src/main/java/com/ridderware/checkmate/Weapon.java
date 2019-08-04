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

import java.awt.Color;
import java.awt.Font;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble3D;

/**
 * Abstract base class for weapons.  Specific weapons will add their own
 * unique attributes and behavior.
 *
 * @author Jeff Ridder
 */
public abstract class Weapon extends MobilePlatform
{
    /**
     *  Parent launcher.
     */
    private IShooterContainer launcher;

    /**
     *  My target.
     */
    private Platform target;

    /**
     * Point from which this missile was fired.
     */
    private Double3D launch_location;

    /**
     * Creates a new instance of Weapon
     * @param name name of the weapon.
     * @param world CMWorld in which it exists.
     */
    public Weapon(String name, CMWorld world)
    {
        super(name, world, 0);
    }

    /**
     * Creates a new instance of Weapon with attributes for GUI display.
     * @param name name of the weapon.
     * @param world CMWorld in which it exists.
     * @param ttf font.
     * @param fontSymbol  font symbol to use for this weapon.
     * @param color color to draw the weapon.
     */
    public Weapon(String name, CMWorld world, Font ttf, String fontSymbol,
        Color color)
    {
        super(name, world, 0, ttf, fontSymbol, color);
    }

    /**
     * Sets the launcher of this weapon.
     * @param launcher launcher.
     */
    public void setLauncher(IShooterContainer launcher)
    {
        this.launcher = launcher;
    }

    /**
     * Returns the launcher of this weapon.
     * @return launcher.
     */
    public IShooterContainer getLauncher()
    {
        return this.launcher;
    }

    /**
     * Sets the target for the weapon.
     * @param target target.
     */
    public void setTarget(Platform target)
    {
        this.target = target;
    }

    /**
     * Returns the target for the weapon.
     * @return target.
     */
    public Platform getTarget()
    {
        return this.target;
    }

    /**
     * Returns the point from which the weapon was launched.
     * @return launch location.
     */
    public Double3D getLaunchLocation()
    {
        return this.launch_location;
    }

    /**
     * Called by the simulation framework to set the initial conditions of the
     * agent prior to each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
        this.setStatus(Platform.Status.INACTIVE);
    }

    /**
     * Returns whether the weapon has been used in the current run.
     * @return true if used, false if not.
     */
    public boolean isUsed()
    {
        boolean used = false;
        if (this.getStatus() == Platform.Status.DEAD)
        {
            used = true;
        }

        return used;
    }

    /**
     * Deactivates the weapon at the specified time.
     * @param current_time current simulation time.
     * @param status status of the deactivated weapon.
     */
    @Override
    public void deactivate(double current_time, Status status)
    {
        this.setStatus(status);
    }

    /**
     * Called to kill the weapon, setting its status to DEAD.
     */
    public void killWeapon()
    {
        //  This should also kill the update behavior in the MobilePlatform super-class.
        this.deactivate(0., Platform.Status.DEAD);
    }

    /**
     * Called by the launcher to shoot the weapon at the target.
     * @param target target.
     * @param launch_location location from which launch occurred.
     */
    public void shootTarget(Platform target, Double3D launch_location)
    {
        this.setStatus(Platform.Status.ACTIVE);
        this.launch_location = launch_location;
        this.setTarget(target);
        this.setHeading(CMWorld.getEarthModel().azimuthAngle(launch_location,
            target.getLocation()));
        this.setElevationAngle(CMWorld.getEarthModel().elevationAngle(launch_location,
            target.getLocation(), IEarthModel.EarthFactor.REAL_EARTH));
        this.setLocation(new MutableDouble3D(launch_location));
        if (getMovePlatformBehavior() != null)
        {
            getMovePlatformBehavior().setTimeOfLastKinematicUpdate(getUniverse().
                getCurrentTime());
            this.getMovePlatformBehavior().setEnabled(true);
            this.getMovePlatformBehavior().setMoving(true);
        }
    }
}
