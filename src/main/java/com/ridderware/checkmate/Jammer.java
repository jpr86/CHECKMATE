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

import java.util.Set;
import com.ridderware.fuse.Double3D;
import org.w3c.dom.Node;

/**
 * Abstract base class for all jammers in the simulation.
 * @author Jeff Ridder
 */
public abstract class Jammer extends CMSystem implements ISIMDIS
{
    private Platform parent;

    /**
     * Creates a new instance of Jammer.
     * @param name name of the jammer.
     * @param world CMWorld in which the jammer plays.
     */
    public Jammer(String name, CMWorld world)
    {
        super(name, world);

        this.createJammer();
    }

    /**
     * Sets the initial attributes of the jammer upon creation.
     */
    protected final void createJammer()
    {
    }

    /**
     * Initializes the jammer before each simulation run.
     */
    public void initialize()
    {

    }

    /**
     * Abstract method for checking whether to issue or retire reactive assignments.
     * @param tracks collection of emitter tracks to be checked for reactive jamming.
     */
    public abstract void checkForReactiveAssignments(Set<Radar> tracks);

    /**
     * Sets the parent platform.
     * @param parent a Platform object.
     */
    public void setParent(Platform parent)
    {
        this.parent = parent;
    }

    /**
     * Returns the parent platform.
     * @return the parent platform.
     */
    public Platform getParent()
    {
        return this.parent;
    }

    /**
     * Abstract method for computing jamming effectiveness against a particular radar.
     * Returns a real number between 0 and 1 to indicate the effect that the jammer
     * has on the target radar.  The radar multiplies one minus the return value by its
     * unjammed detection range to determine the jammed detection range.  A return
     * value of 0 indicates no jamming effectiveness, while a return value of 1 is
     * unachievably perfect effectiveness.
     * @param tgt a Radar object.
     * @return value between 0 and 1 indicating the jamming effectiveness.
     */
    public abstract double jammingEffectiveness(Radar tgt);

    /**
     * Method to compute the relative gain of the radar based on its orientation to the jammer.
     * By default, we assume that jammers jam the backlobes (average sidelobe) of radars.
     * However, when the radar is a DirectionalRadar, then we must check to see whether
     * we are in a mainlobe or sidelobe, and use the appropriate gain.
     * @param tgt target radar
     * @return relative gain (absolute, not dB).
     */
    public double getRelativeGain(Radar tgt)
    {
        double rel_gain = 1.;

        if (tgt instanceof DirectionalRadar)
        {
            DirectionalRadar r = (DirectionalRadar) tgt;

            if (r.getAntenna() != null)
            {
                Double3D r_loc = r.getParent().getLocation();
                Double3D j_loc = this.getParent().getLocation();
                //  Get angle theta and phi between radar and jammer, and compare to the antenna boresight.
                double theta = CMWorld.getEarthModel().azimuthAngle(r_loc,
                    j_loc);
                double phi = CMWorld.getEarthModel().elevationAngle(r_loc,
                    j_loc, IEarthModel.EarthFactor.EM_EARTH);

                //  Get az and el angles relative to antenna boresight
                double az = theta - r.getAntenna().getBoresight().getX();
                double el = Math.abs(phi - r.getAntenna().getBoresight().getY());

                az = Math.abs(AngleUnit.normalizeAngle(az, -Math.PI));

                if (az <= r.getAntenna().getMainlobeAz() && el <= r.getAntenna().
                    getMainlobeEl())
                {
                    //  We're in the mainbeam
                    rel_gain = DBUnit.idB(r.getAntenna().getMainlobe() - r.getAntenna().
                        getAveSidelobe());
                }
                else if (az <= r.getAntenna().getSidelobeAz() && el <= r.getAntenna().
                    getSidelobeEl())
                {
                    //  We're in the first sidelobe
                    rel_gain = DBUnit.idB(r.getAntenna().getSidelobe() - r.getAntenna().
                        getAveSidelobe());
                }
            }
        }
        return rel_gain;
    }

    /**
     * Sets the attributes of the jammer by parsing the XML.
     *
     * @param node root node for the jammer.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);
    }

    /**
     * Creates XML nodes containing the jammer's attributes.
     *
     * @param node root node for the jammer.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);
    }
}
