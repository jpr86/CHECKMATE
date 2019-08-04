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
import java.awt.Shape;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.gui.Painter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A representation of a SAM Transporter-Erector-Launcher.  Weapons are subordinate
 * platforms.
 *
 * @author Jeff Ridder
 */
public class SAMTEL extends UncertainLocationPlatform implements IShooterContainer
{
    Platform assigned_target = null;

    /**
     * Creates a new instance of SAMTEL.
     *
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     */
    public SAMTEL(String name, CMWorld world, int points)
    {
        super(name, world, points);

    }

    /**
     * Creates a new instance of SAMTEL with attributes for GUI display.
     *
     * @param name name of the platform.
     * @param world CMWorld in which the platform plays.
     * @param points point value of the platform.
     * @param ttf font containing the symbol to display.
     * @param fontSymbol the font symbol to display for this object.
     * @param color color to use for this symbol.
     */
    public SAMTEL(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
    }

    /**
     * Sets the initial attributes of the platform before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
        //  Loop over subordinates and reset them?  No...the universe will do that.

        assigned_target = null;
    }

    /**
     * Called by the framework to paint the agent.
     * @param args arguments.
     * @return collection of shapes to draw.
     */
    @Override
    public Collection<Shape> paintAgent(Object... args)
    {
        Collection<Shape> boundingShapes = new HashSet<>();

        if (getSuperior() instanceof SAMSite && (getSuperior().getState().
            getName().equals("Deployed") ||
            getSuperior().getState().getName().equals("Firing")))
        {
            Color gray = Color.gray;
            Color color = new Color(gray.getRed(), gray.getGreen(),
                gray.getBlue(), 128);

            if (getSuperior().getState().getName().equals("Firing") &&
                this.assigned_target != null)
            {
                color = this.getColor();
            }

            Double3D loc = null;
            if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("ENU"))
            {
                loc = this.getLocation();
            }
            else if (CMWorld.getEarthModel().getCoordinateSystem().
                equalsIgnoreCase("LLA"))
            {
                loc = new Double3D(getLocation().getY(), getLocation().getX(), getLocation().
                    getZ());
            }
            if (loc != null)
            {
                boundingShapes.add(Painter.getPainter().paintText(this.getFontSymbol(),
                    loc, color, this.getFont(), 0.));
            }

        }

        return boundingShapes;
    }

    /**
     * Sets the platform's attributes by parsing the XML.
     * @param node root node for the platform.
     */
    @Override
    public void fromXML(Node node)
    {
        //  Weapons will be specified within the SAMTEL Tags
        //  This differs from the usual subordinate/superior procedure, but
        //  makes sense here because weapons are entirely contained by the
        //  SAMTEL.  i.e., we treat them more like systems contained by the platform.

        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("Weapon"))
            {
                Set<Weapon> weapons = getWorld().createWeapons(child);

                for (Weapon w : weapons)
                {
                    this.getSubordinates().add(w);
                    w.setLauncher(this);
                    w.setSuperior(this);
                }
            }
        }
    }

    /**
     * Creates XML nodes containing the platform's attributes.
     *    @param node root node for the platform.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        //  Go through the subordinates and find those whose names are the same.
        Set<Platform> subs = new HashSet<Platform>(this.getSubordinates());

        while (!subs.isEmpty())
        {
            Weapon cur_weapon = null;

            int num = 0;

            for (Platform p : subs)
            {
                if (p instanceof Weapon)
                {
                    if (cur_weapon != null &&
                        p.getName().equals(cur_weapon.getName()))
                    {
                        num++;
                        subs.remove(p);
                    }
                    else if (cur_weapon == null)
                    {
                        cur_weapon = (Weapon) p;
                        num = 1;
                        subs.remove(p);
                    }
                }
                else
                {
                    subs.remove(p);
                }
            }

            if (cur_weapon != null)
            {
                Element e = document.createElement("Weapon");
                e.setAttribute("num", String.valueOf(num));
                getWorld().writeWeaponToXML(cur_weapon, e);
                node.appendChild(e);
            }
        }
    }

    /**
     * Computes and returns the engageability figure-of-merit for the platform
     * with regard to the specified target.  This is comuted as the ratio of the
     * range of the target to any subordinate weapons.
     * @param target target to be considered.
     * @return engageability figure-of-merit of the target.
     */
    @Override
    public double getEngageability(Platform target)
    {
        double engageability = Double.MAX_VALUE;

        if (assigned_target == null)
        {

            for (Platform p : this.getSubordinates())
            {
                if (p instanceof SAM)
                {
                    SAM s = (SAM) p;

                    if (s.getStatus() == Platform.Status.INACTIVE && getWorld().
                        getRealLOSUtil().hasLOS(this, target))
                    {
                        engageability =
                            Math.min(engageability,
                            CMWorld.getEarthModel().trueDistance(getLocation(),
                            target.getLocation()) / s.getLethalRange());
                    }
                }
            }
        }
        return engageability;
    }

    /**
     * Assigns the specified target to the platform.
     * @param target target being assigned.
     */
    @Override
    public void assignTarget(Platform target)
    {
        assigned_target = target;
    }

    /**
     * Called by the superior to assign a higher priority target to the shooter.
     * If the shooter has no capacity, it will displace a current target to accept this one.
     * @param target priority target being assigned.
     */
    @Override
    public void priorityAssignTarget(Platform target)
    {
        this.assignTarget(target);
    }

    /**
     * Called by a superior SAMSite to command the platform to fire a SAM at the
     * target in the specified guidance mode.
     * @param target target platform.
     * @param guidance_mode SAM guidance mode.
     */
    public void engageTarget(Platform target, SAM.GuidanceMode guidance_mode)
    {
        if (target != assigned_target)
        {
            assigned_target = target;
        }

        //  Find a weapon that is unused.
        for (Platform p : this.getSubordinates())
        {
            if (p instanceof SAM && p.getStatus() == Platform.Status.INACTIVE)
            {
                SAM s = (SAM) p;
                s.setGuidanceMode(guidance_mode);
                s.shootTarget(target, this.getLocation());
                break;
            }
        }
    }

    /**
     * Called by the fired SAM to inform the TEL as to the outcome of the
     * engagement.  This is forwarded to upper echelon.
     * @param target engaged platform.
     * @param destroyed true if target was destroyed, false otherwise.
     */
    @Override
    public void targetDestroyed(Platform target, boolean destroyed)
    {
        //  Report to upper echelon.  If destroyed then remove target.  Otherwise
        //  re-engage if conditions are appropriate.
        if (destroyed)
        {
            assigned_target = null;
        }

        if (getSuperior() instanceof IShooterContainer)
        {
            ((IShooterContainer) getSuperior()).targetDestroyed(target,
                destroyed);
        }
    }

    /**
     * Unassigns the specifed target from the platform.
     * @param target target being unassigned.
     */
    @Override
    public void unassignTarget(Platform target)
    {
        assigned_target = null;
    }
}
