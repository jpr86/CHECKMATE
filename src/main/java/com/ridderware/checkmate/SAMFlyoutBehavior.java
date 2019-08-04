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

import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble3D;
import org.apache.logging.log4j.*;
import org.w3c.dom.Node;

/**
 * A behavior that controls the movement of SAM objects.
 *
 * @author Jeff Ridder
 */
public class SAMFlyoutBehavior extends MovePlatformBehavior
{
    private double start_kinematic_update_period;

    private static boolean bThrottled;

    private boolean imThrottled;

    private static int samsThrottled;

    private static final Logger logger =
        LogManager.getLogger(SAMFlyoutBehavior.class);

    /** Creates a new instance of SAMFlyoutBehavior
     * @param platform SAM object controlled by this behavior.
     */
    public SAMFlyoutBehavior(SAM platform)
    {
        super(platform);
    }

    /**
     * Initializes the behavior before each simulation run.
     */
    @Override
    public void initialize()
    {
        super.initialize();

        if (bThrottled)
        {
            this.setKinematicUpdatePeriod(start_kinematic_update_period);
        }
        bThrottled = false;
        samsThrottled = 0;
        imThrottled = false;
    }

    /**
     * Updates the guidance mode based on current conditions.
     * @param time current time.
     */
    protected void updateGuidanceMode(double time)
    {
        Radar ttr = null;
        SAMSite site = null;

        SAM p = (SAM) getPlatform();

        //  First, get the TTR.
        if (p.getLauncher() instanceof SAMTEL)
        {
            SAMTEL tel = (SAMTEL) p.getLauncher();

            if (tel.getSuperior() instanceof SAMSite)
            {
                site = (SAMSite) tel.getSuperior();

                if (site.getEngagedTarget() != null)
                {
                    ttr = site.getEngagedTarget().getTTR();
                }
            }
        }

        //  If mode is currently ballistic, then check to see if target is now
        //  being actively tracked.  If so, then go semi-active.

        //  If mode is currently semi-active, then look for
        //  1) within active guidance range
        //  2) that TT is still tracking -- remain semi-active
        //  3) TT not tracking and not jammed -- go Ballistic
        //  4) that TT is jammed -- switch to HOJ and change targets to the jamming source.

        // If mode is currently active, then do nothing

        //  If mode is currently HOJ, then check that target is still jamming my ttr.
        //  If not, then go ballistic.

        switch (p.getGuidanceMode())
        {
            case BALLISTIC:
                if (ttr != null && site != null)
                {
                    if (site.getTrackData(p.getTarget()) != null &&
                        site.getTrackData(p.getTarget()).tt_radar == ttr)
                    {
                        //  Then actively tracking my target, switch to semi-active.
                        p.setGuidanceMode(SAM.GuidanceMode.SEMIACTIVE);
                    }
                }
                break;
            case SEMIACTIVE:
                if (ttr != null && site != null)
                {
                    //  If ttr is not tracking target
                    //  If ttr is jammed, then HOJ
                    //  Iff ttr !jammed, then ballistic

                    if (site.getTrackData(p.getTarget()) == null ||
                        (site.getTrackData(p.getTarget()) != null &&
                        site.getTrackData(p.getTarget()).tt_radar != ttr))
                    {
                        if (ttr.isJammed())
                        {
                            //  Flip to HOJ and change target to the jammer
                            p.setTarget(ttr.getJammingSource().getParent());
                            p.setGuidanceMode(SAM.GuidanceMode.HOJ);
                        }
                        else
                        {
                            p.setGuidanceMode(SAM.GuidanceMode.BALLISTIC);
                        }
                    }
                }

                if (p.getLocation().distance(p.getTarget().getLocation()) <
                    p.getActiveGuidanceRange())
                {
                    p.setGuidanceMode(SAM.GuidanceMode.ACTIVE);
                }

                break;
            case ACTIVE:
                break;
            case HOJ:
                if (ttr != null)
                {
                    if (ttr.getJammingSource() != null)
                    {
                        if (ttr.getJammingSource().jammingEffectiveness(ttr) <=
                            0.)
                        {
                            p.setGuidanceMode(SAM.GuidanceMode.BALLISTIC);
                        }
                    }
                }
                break;
            default:
        }
    }

    /**
     * Moves the SAM platform to the requested point in time.
     * @param time current simulation time.
     */
    @Override
    public void movePlatform(double time)
    {
        SAM p = (SAM) getPlatform();

        //  When we are firing a missile, it is necessary to
        //  ensure that the target position is up-to-date.  Therefore, we call that first.
        Platform target = p.getTarget();
        double prev_angle = 0.;
        if (target != null && target instanceof MobilePlatform)
        {
            prev_angle =
                CMWorld.getEarthModel().azimuthAngle(p.getLocation(),
                target.getLocation());
            ((MobilePlatform) target).movePlatform(time);
        }

        //  First, update the guidance mode
        this.updateGuidanceMode(time);

        //  Move the missile.  Look for either exceeding lethal range
        //  or target proximity.
        //  Upon detonating the missile, must take steps to stop updating
        //  the missile -- stop the update behavior and kill the paint.

        double delta_time = time - this.getTimeOfLastKinematicUpdate();

        double distance = delta_time * p.getSpeed() / 3600.;

        //  There are 4 guidance modes:  Semi-active, active, ballistic, and HOJ
        //  Must account for all 3 here.
        //  1) Active -- Heading adapts to target location regardless of external illumination.
        //  2) Semi-active -- Heading adapts to target as long as there is external illumination.
        //  2) Ballistic -- Heading does not change.
        //  3) HOJ -- Heading adapts to target location as long as Jammer
        //  is on and emitting in a frequency I expect -- DO LATER.

        //  Missile is killed upon one of the following conditions:
        //  1) It has flown beyond max range
        //  2) It has flown within proximity of the target aircraft (results in
        //     detonation and Pk roll of dice.

        //  Distance to target
        double dt = CMWorld.getEarthModel().trueDistance(p.getLocation(),
            target.getLocation());

        p.accrueDistanceMoved(distance);

        //  First, move the missile


        switch (p.getGuidanceMode())
        {
            case ACTIVE:
            case SEMIACTIVE:
            case HOJ:

                double max_gs = p.getMaxGs();
                double max_heading_change = delta_time *
                    32.12 * Math.sqrt(max_gs * max_gs - 1.) / (1.68781 *
                    p.getSpeed());

                double angle_to_target =
                    CMWorld.getEarthModel().azimuthAngle(p.getLocation(),
                    target.getLocation());

                double diff_angle = angle_to_target - p.getHeading();
                diff_angle =
                    (diff_angle > Math.PI ? diff_angle - 2. * Math.PI : diff_angle);
                diff_angle = (diff_angle < -Math.PI ? diff_angle + 2. * Math.PI
                    : diff_angle);
                double del_angle = angle_to_target - prev_angle;
                del_angle =
                    (del_angle > Math.PI ? del_angle - 2. * Math.PI : del_angle);
                del_angle =
                    (del_angle < -Math.PI ? del_angle + 2. * Math.PI : del_angle);

                if (Math.abs(del_angle) > 0.15 * max_heading_change &&
                    Math.abs(diff_angle) < 5. * max_heading_change)
                {
                    //  Target's angle rate is getting high...pull max g's.
                    diff_angle = Math.signum(del_angle) * max_heading_change;
                }
                else
                {
                    if (Math.abs(diff_angle) > max_heading_change)
                    {
                        if (diff_angle > 0.)
                        {
                            diff_angle = max_heading_change;
                        }
                        else
                        {
                            diff_angle = -max_heading_change;
                        }
                    }
                }

                double new_heading = p.getHeading() + diff_angle;
                new_heading = AngleUnit.normalizeAngle(new_heading, -Math.PI);

                p.setHeading(new_heading);

                p.setElevationAngle(CMWorld.getEarthModel().elevationAngle(p.getLocation(),
                    target.getLocation(), IEarthModel.EarthFactor.REAL_EARTH) - CMWorld.getEarthModel().
                    elevationAngle(p.getLocation(), new Double3D(target.getLocation().
                    getX(), target.getLocation().getY(),
                    p.getLocation().getZ()), IEarthModel.EarthFactor.REAL_EARTH));

            case BALLISTIC:
                //  Heading does not change...fly the heading.
                double xydist = distance * Math.cos(p.getElevationAngle());
                MutableDouble3D new_loc = new MutableDouble3D(CMWorld.getEarthModel().
                    projectLocation(p.getLocation(), xydist, p.getHeading()));

                new_loc.setZ(p.getLocation().getZ() + distance *
                    Math.sin(p.getElevationAngle()));

                p.setLocation(new_loc);
                break;
            default:
        }

        //  Next, let's look for target kills...don't care what the guidance mode is,
        if (distance >= dt)
        {
            //  This is a hack because the heading may change suddenly due to the pure pursuit
            //  course we are flying.  Would be better to up the turn rate prior to hitting the target,
            //  anticipating the end game.
            p.setHeading(CMWorld.getEarthModel().azimuthAngle(p.getLocation(),
                target.getLocation()));
            p.setElevationAngle(CMWorld.getEarthModel().elevationAngle(p.getLocation(),
                target.getLocation(), IEarthModel.EarthFactor.REAL_EARTH));
            p.setLocation(target.getLocation());

            //  We flew through the target and detonated
            if (p.getRNG().nextDouble() < p.getPk())
            {
                //  He gone!
                ((Aircraft) target).deactivate(time, Platform.Status.DEAD);

                logger.debug("Aircraft[" + target.getId() +
                    "] destroyed by SAM " + p.getName() +
                    " launched from Site " + p.getSuperior().getSuperior().
                    getName());

                //  Report to the launcher that I killed the target.
                p.getLauncher().targetDestroyed(target, true);
            }
            else
            {
                //  Report the miss to the launcher.
                p.getLauncher().targetDestroyed(target, false);

                logger.debug("Aircraft[" + target.getId() + "] missed by SAM " +
                    p.getName() +
                    " launched from Site " + p.getSuperior().getSuperior().
                    getName());
            }

            p.killWeapon();

            if (imThrottled)
            {
                samsThrottled--;
                imThrottled = false;
            }

            if (bThrottled && samsThrottled == 0)
            {
                this.setKinematicUpdatePeriod(this.start_kinematic_update_period);
                bThrottled = false;
            }
        }
        else
        {
            //  Didn't kill target

            this.setTimeOfLastKinematicUpdate(time);

            //  If I'm beyond lethal range, then self-destruct.
            //  If target is out of range then stop this.
            if (p.getDistanceMoved() > p.getLethalRange())
            {
                p.getLauncher().targetDestroyed(p.getTarget(), false);
                p.killWeapon();
            }

            if (dt < 0.15 * p.getLethalRange() && !imThrottled)
            {
                samsThrottled++;
                imThrottled = true;

                if (!bThrottled)
                {
                    this.setKinematicUpdatePeriod(0.1 *
                        this.start_kinematic_update_period);
                    bThrottled = true;
                }
            }
        }
    }

    /**
     * Method to activate the SAM.
     * @param time time at which to activate.
     */
    @Override
    public void activate(double time)
    {
    }

    /**
     * Parses the XML node to set the attributes of the object.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        this.start_kinematic_update_period = this.getKinematicUpdatePeriod();
    }
}
