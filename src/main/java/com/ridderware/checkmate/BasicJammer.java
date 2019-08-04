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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import com.ridderware.fuse.Double3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A basic jammer class that jams all emitters in the environment.  Effectiveness
 * is at two levels -- preemptive and reactive.
 * @author Jeff Ridder
 */
public class BasicJammer extends Jammer
{
    private double pa_effectiveness;

    private double ra_effectiveness;

    private double reference_range;

    private boolean simdis_beams_on = false;

    private HashSet<Radar> ras = new HashSet<Radar>();

    /**
     * Creates a new instance of BasicJammer.
     * @param name name of the jammer.
     * @param world CMWorld in which the jammer plays.
     * @param pa_effectiveness preemptive effectiveness of the jammer.  A number between 0 and 1 indicating
     * how effective preemptive techniques are against emitters from the reference range.
     * @param ra_effectiveness reactive effectiveness of the jammer.  A number between 0 and 1 indicating
     * how effective reactive techniques are against emitters from the reference range.
     * @param reference_range the reference range for the specified effectiveness values.
     */
    public BasicJammer(String name, CMWorld world, double pa_effectiveness,
        double ra_effectiveness, double reference_range)
    {
        super(name, world);

        this.pa_effectiveness = pa_effectiveness;
        this.ra_effectiveness = ra_effectiveness;
        this.reference_range = reference_range;

        this.createBasicJammer();
    }

    /**
     * Sets the initial attributes of the jammer upon creatino.
     */
    protected final void createBasicJammer()
    {

    }

    /**
     * Initializes the jammer before each simulation run.
     */
    @Override
    public void initialize()
    {
        super.initialize();

        simdis_beams_on = false;

        ras.clear();
    }

    /**
     * Override of base class method to check for reactive assignments.
     * @param tracks tracks to be checked for reactive response.
     */
    @Override
    public void checkForReactiveAssignments(Set<Radar> tracks)
    {
        ras.clear();
        ras.addAll(tracks);
    }

    /**
     * Returns a real number between 0 and 1 to indicate the effect that the jammer
     * has on the target radar.  The radar multiplies one minus the return value by its
     * unjammed detection range to determine the jammed detection range.
     * @param tgt a Radar object.
     * @return value between 0 and 1 indicating the jamming effectiveness.
     */
    @Override
    public double jammingEffectiveness(Radar tgt)
    {
        double k = 1. - this.pa_effectiveness;

        if (ras.contains(tgt))
        {
            k = 1. - this.ra_effectiveness;
        }

        double k4 = k * k * k * k;

        //  getRelativeGain checks for directional radars, and whether we're in the mainlobe or sidelobe.
        double rr2 = this.getRelativeGain(tgt) * reference_range *
            reference_range / CMWorld.getEarthModel().trueDistanceSq(getParent().
            getLocation(), tgt.getParent().getLocation());

        double fj = 1. - k * Math.sqrt(Math.sqrt(1. / (k4 + (1. - k4) * rr2)));

        return fj;
    }

    /**
     * Sets the jammer's parameters by parsing the input XML node.
     * @param node root XML node containing the jammer's parameters.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();
        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);
            if (child.getNodeName().toLowerCase().equals("reference-range"))
            {
                this.reference_range =
                    Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("pa-effectiveness"))
            {
                this.pa_effectiveness =
                    Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("ra-effectiveness"))
            {
                this.ra_effectiveness =
                    Double.parseDouble(child.getTextContent());
            }
        }
    }

    /**
     * Creates DOM XML nodes containing the jammer's parameters.
     * @param node root XML node of the jammer.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        e = document.createElement("reference-range");
        e.setTextContent(String.valueOf(this.reference_range));
        node.appendChild(e);

        e = document.createElement("pa-effectiveness");
        e.setTextContent(String.valueOf(this.pa_effectiveness));
        node.appendChild(e);

        e = document.createElement("ra-effectiveness");
        e.setTextContent(String.valueOf(this.ra_effectiveness));
        node.appendChild(e);
    }

    /**
     * Writes the beam initialization for this jammer to the SIMDIS asi file.
     * @param asi_file SIMDIS asi file.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
        if (asi_file != null && getParent().getSIMDISIcon() != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                //  We need to declare a beam ID for every jammer beam that this thing could emit.
                //  Up to 1 beam for every radar that could be covered
                //  Get the technique libraries that apply here.
                Set<String> beam_ids = new HashSet<String>();

                for (Radar r : getWorld().getRadars())
                {
                    beam_ids.add(String.valueOf(this.getId()) +
                        String.valueOf(r.getId()));
                }

                for (String beam_id : beam_ids)
                {
                    pw.println("BeamID\t" + String.valueOf(getParent().getId()) +
                        "\t" + beam_id);
                    pw.println("VertBW\t" + beam_id + "\t1.");
                    pw.println("HorzBW\t" + beam_id + "\t1.");
                    pw.println("BodyOffset\t" + beam_id + "\t0.\t0.\t0.");
                }

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Writes beam position updates to the SIMDIS asi file.
     * @param time time of the update.
     * @param asi_file SIMDIS asi file.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        if (getParent().getSIMDISIcon() != null && asi_file != null)
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                //  Every radar is covered by at least a PA.  Also see if we cover it with an RA.               
                for (Radar r : getWorld().getRadars())
                {
                    String beam_id = String.valueOf(this.getId()) +
                        String.valueOf(r.getId());

                    String color = "yellow";
                    if (this.ras.contains(r))
                    {
                        color = "blue";
                    }

                    if (!simdis_beams_on)
                    {
                        pw.println("BeamOnOffCmd\t" + beam_id + "\t" +
                            String.valueOf(time) + "\t1");
                    }

                    Double3D aimpoint = r.getParent().getLocation();
                    double az_angle = CMWorld.getEarthModel().azimuthAngle(getParent().
                        getLocation(), aimpoint);
                    double el_angle = CMWorld.getEarthModel().elevationAngle(getParent().
                        getLocation(), aimpoint,
                        IEarthModel.EarthFactor.REAL_EARTH);
                    double distance = LengthUnit.NAUTICAL_MILES.convert(CMWorld.getEarthModel().
                        trueDistance(getParent().getLocation(), aimpoint),
                        LengthUnit.METERS);

                    pw.println("BeamData\t" + beam_id + "\t" + time + "\t" +
                        color + "\t" + az_angle + "\t" + el_angle + "\t" +
                        distance);
                }

                simdis_beams_on = true;

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }
}
