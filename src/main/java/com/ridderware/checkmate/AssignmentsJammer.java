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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import com.ridderware.fuse.Double2D;
import com.ridderware.fuse.Double3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A type of jammer that implements specific jamming assignments against radars.
 * Assignments are based purely on classification.  There is no directionality of
 * coverage (i.e., omnidirectional) and there is no test of reactive
 * assignments for geo-feasibility.
 *
 * @author Jeff Ridder
 */
public class AssignmentsJammer extends Jammer
{
    //  The maximum number of assignments this jammer can carry.
    private int total_resources;

    //  We keep track of the current assignments in two ways:
    //  1) A set of all assignments so that we can quickly determine how many jamming
    //     resources have been expended and how many are available.
    //  2) Maps of specific radars to preemptive and reactive jamming assignments to enable rapid lookup
    //     of jamming vs. a radar.
    //  This is the set of current assignments
    private HashSet<JammingAssignment> current_assignments =
        new HashSet<JammingAssignment>();

    //  This is the map of each radar (key) to the preemptive assignment covering
    private HashMap<Radar, JammingAssignment> pa_coverages =
        new HashMap<Radar, JammingAssignment>();

    //  This is the map of each radar (key) to the reactive assignment covering
    private HashMap<Radar, JammingAssignment> ra_coverages =
        new HashMap<Radar, JammingAssignment>();

    //  The following two sets (PA's and RA's) represent the jam plan -- the pool of
    //  available actions (assignments) that can be taken during the course of the mission.
    //  PA's will always happen, while RA's will only happen in response to an active ES track
    //  if sufficient jamming resources are available.
    //  The set of PA's that will be created at the beginning of the mission.
    private HashSet<JammingAssignment> preemptive_assignments =
        new HashSet<JammingAssignment>();

    /** The set of beams that are currently being drawn by SIMDIS */
    protected Set<String> simdis_beams_on = new HashSet<String>();

    //  A map of radar classification to RA's.
    private HashMap<Integer, JammingAssignment> reactive_assignments =
        new HashMap<Integer, JammingAssignment>();

    /**
     * Creates a new instance of AssignmentsJammer.
     * @param name name of the jammer.
     * @param world CMWorld in which the jammer plays.
     */
    public AssignmentsJammer(String name, CMWorld world)
    {
        super(name, world);

        this.createAssignmentsJammer();
    }

    /**
     * Sets the initial attributes of an AssignmentsJammer upon object creation.
     */
    protected final void createAssignmentsJammer()
    {
        this.total_resources = 1;
    }

    /**
     * Sets the total number of jamming resources available on this jammer.
     * @param total_resources total number of jamming resources.
     */
    public void setTotalResources(int total_resources)
    {
        this.total_resources = total_resources;
    }

    /**
     * Returns the total number of jamming resources available on this jammer.
     *
     * @return total number of jamming resources.
     */
    public int getTotalResources()
    {
        return this.total_resources;
    }

    /**
     * Initializes the attributes of the jammer before each simulation run.
     */
    @Override
    public void initialize()
    {
        super.initialize();

        //  Reset jamming assignments to initial state
        current_assignments.clear();
        pa_coverages.clear();
        ra_coverages.clear();

        simdis_beams_on.clear();

        int num_resources = 0;
        //  Now load PAs until we run out of resources.
        for (JammingAssignment pa : preemptive_assignments)
        {
            num_resources += pa.getResourcesRequired();
            if (num_resources <= this.total_resources)
            {
                for (Integer classification : pa.getRadarsCovered())
                {
                    for (Radar r : getWorld().getRadars())
                    {
                        if (r.getClassification() == classification)
                        {
                            pa_coverages.put(r, pa);
                        }
                    }
                }
            }
        }

        simdisUpdate(getParent().getUniverse().getCurrentTime(), getWorld().
            getASIFile());
    }

    /**
     * Adds a preemptive assignment to the jammer.  Preemptive assignments will be
     * maintained throughout the simulation run.
     * @param pa a JammingAssignment object with preemptive attributes.
     */
    public void addPreemptiveAssignment(JammingAssignment pa)
    {
        this.preemptive_assignments.add(pa);
        pa.setAssignmentType(JammingAssignment.AssignmentType.PREEMPTIVE);
    }

    /**
     * Adds a reactive assignment to the jammer.  Reactive assignments are issued
     * dynamically throughout the simulation run in response to the environment
     * @param ra a JammingAssignment object with reactive attributes.
     */
    public void addReactiveAssignment(JammingAssignment ra)
    {
        this.reactive_assignments.put(ra.getRadarsCovered()[0], ra);
        ra.setAssignmentType(JammingAssignment.AssignmentType.REACTIVE);
    }

    /**
     * Uses the input track data to check for reactive jamming conditions.  If any
     * conditions are met, then new reactive assignments are issued.  If conditions
     * are no longer sufficient to maintain existing reactive assignments, they are
     * dropped.
     * @param tracks tracks of emitters.
     */
    @Override
    public void checkForReactiveAssignments(Set<Radar> tracks)
    {
        //  First, look for reactive assignments on emitters that are no
        //  longer actively tracked.  Remove them.

        Set<Radar> keys = ra_coverages.keySet();
        Iterator<Radar> it = keys.iterator();
        while (it.hasNext())
        {
            Radar r = it.next();
            if (ra_coverages.get(r).getAssignmentType() ==
                JammingAssignment.AssignmentType.REACTIVE &&
                !tracks.contains(r))
            {
                it.remove();
            }
        }

        keys = ra_coverages.keySet();

        //  Number of resources currently in use.
        int num_resources = this.getNumResources();

        //  Loop over all the tracks.
        //  RA will happen IF:
        //  1) The radar is not already reactively jammed.
        //  2) It is classification eligible.
        //  3) There are sufficient resources for the assignment.
        //  Extensions of this class could also check other criteria, such as
        //  geo-feasibility.
        for (Radar r : tracks)
        {
            if (!keys.contains(r) &&
                reactive_assignments.containsKey(r.getClassification()) &&
                num_resources + reactive_assignments.get(r.getClassification()).
                getResourcesRequired() <= this.total_resources)
            {
                JammingAssignment ra = reactive_assignments.get(r.getClassification()).
                    clone();

                ra_coverages.put(r, ra);

                num_resources += ra.getResourcesRequired();
            }
        }

        simdisUpdate(getParent().getUniverse().getCurrentTime(), getWorld().
            getASIFile());
    }

    /**
     * Computes and returns the number of jamming resources currently being consumed.
     * @return number of jamming resources used.
     */
    public int getNumResources()
    {
        current_assignments.clear();
        current_assignments.addAll(this.pa_coverages.values());
        current_assignments.addAll(this.ra_coverages.values());
        int num = 0;
        for (JammingAssignment a : current_assignments)
        {
            num += a.getResourcesRequired();
        }

        return num;
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
        double fj = 0.;

        JammingAssignment a = this.ra_coverages.get(tgt);
        if (a == null)
        {
            a = this.pa_coverages.get(tgt);
        }

        if (a != null)
        {
            //  I'm assuming this distance is in n.mi.  If not, then need to fix the calculation here.
            double distanceSq = CMWorld.getEarthModel().trueDistanceSq(getParent().
                getLocation(), tgt.getParent().getLocation());

            Double2D assign_effectiveness = a.getCoverage(tgt);

            double fj0 = assign_effectiveness.getX();
            double rj0 = assign_effectiveness.getY();

            double k = 1. - fj0;
            double k4 = k * k * k * k;

            //  This adjusts jamming effectiveness for range and number of
            // jamming resources in use (assumes time multiplexing).
            fj = 1. - k * Math.sqrt(Math.sqrt(1. / (k4 + (1. - k4) *
                this.getRelativeGain(tgt) * rj0 * rj0 / distanceSq /
                getNumResources())));
        }

        return fj;
    }

    /**
     * Returns the map of individual radars to the preemptive assignments
     * covering those radars (if any).
     * @return map of current preemptive coverages
     */
    protected HashMap<Radar, JammingAssignment> getPACoverages()
    {
        return pa_coverages;
    }

    /**
     * Returns the map of individual radars to the reactive assignments
     * covering those radars (if any).
     * @return map of current reactive coverages
     */
    protected HashMap<Radar, JammingAssignment> getRACoverages()
    {
        return ra_coverages;
    }

    /**
     * Returns the pool of preemptive assignments to be issued by this jammer.
     * This represents the list of planned preemptive assignments, not the current
     * PA's.  For those, look to getPACoverages.
     * @return the planned preemptive assignments.
     */
    public HashSet<JammingAssignment> getPreemptiveAssignments()
    {
        return preemptive_assignments;
    }

    /**
     * Returns the pool of reactive assignments to be issued by this jammer.
     * This represents the list of planned reactive assignments, not the current
     * RA's.  For those, look to getRACoverages.
     * @return the planned reactive assignments.
     */
    public HashMap<Integer, JammingAssignment> getReactiveAssignments()
    {
        return reactive_assignments;
    }

    /**
     * Sets the jammer's parameters by parsing the XML.
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
            if (child.getNodeName().equalsIgnoreCase("jamming-assignment"))
            {
                JammingAssignment a = new JammingAssignment();
                a.fromXML(child);


                if (a.getAssignmentType() ==
                    JammingAssignment.AssignmentType.REACTIVE)
                {
                    this.addReactiveAssignment(a);
                }
                else
                {
                    this.addPreemptiveAssignment(a);
                }
            }
            else if (child.getNodeName().equalsIgnoreCase("total-resources"))
            {
                this.total_resources = Integer.parseInt(child.getTextContent());
            }
        }
    }

    /**
     * Creates DOM XML nodes containing the jammer's parameters.
     * @param node root node of the jammer.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        for (JammingAssignment a : this.preemptive_assignments)
        {
            //  Create elephants
            e = document.createElement("jamming-assignment");
            e.setAttribute("type", a.getAssignmentType().toString());
            node.appendChild(e);

            a.toXML(e);
        }

        Set<Integer> radars = this.reactive_assignments.keySet();
        for (Integer i : radars)
        {
            JammingAssignment a = this.reactive_assignments.get(i);

            //  Create elephants
            e = document.createElement("jamming-assignment");
            e.setAttribute("type", a.getAssignmentType().toString());
            node.appendChild(e);

            a.toXML(e);
        }

        e = document.createElement("total-resources");
        e.setTextContent(String.valueOf(this.total_resources));
        node.appendChild(e);
    }

    /**
     * Writes the initialization of jammer beams to the specified SIMDIS file.
     *
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
                    beam_ids.add(String.valueOf(this.getId()) + r.getId());
                }

                for (String beam_id : beam_ids)
                {
                    pw.println("BeamID\t" + getParent().getId() + "\t" + beam_id);
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
     * Write updates of jammer beam positions to the SIMDIS asi file.
     * @param time time of update.
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

                // 1) First, form the set of PA and RA covered radars
                Set<Radar> pas = pa_coverages.keySet();
                Set<Radar> ras = ra_coverages.keySet();

                // 2) Let's make sure we put out a hit for every assignment that is turned on or off
                for (Radar r : pas)
                {
                    String beam_id = String.valueOf(this.getId()) +
                        String.valueOf(r.getId());

                    if (simdis_beams_on.add(beam_id))
                    {
                        pw.println("BeamOnOffCmd\t" + beam_id + "\t" + time +
                            "\t1");
                    }
                }

                for (Radar r : ras)
                {
                    String beam_id = String.valueOf(this.getId()) +
                        String.valueOf(r.getId());

                    if (simdis_beams_on.add(beam_id))
                    {
                        pw.println("BeamOnOffCmd\t" + beam_id + "\t" +
                            String.valueOf(time) + "\t1");
                    }
                }

                //  3) Now go back and turn remove any beams that are no longer on
                String my_id = String.valueOf(this.getId());
                Iterator it = simdis_beams_on.iterator();
                while (it.hasNext())
                {
                    String beam_id = (String) it.next();

                    int radar_id =
                        Integer.parseInt(beam_id.substring(my_id.length()));

                    boolean bfound = false;
                    for (Radar r : pas)
                    {
                        if (r.getId() == radar_id)
                        {
                            bfound = true;
                            break;
                        }
                    }

                    if (!bfound)
                    {
                        for (Radar r : ras)
                        {
                            if (r.getId() == radar_id)
                            {
                                bfound = true;
                                break;
                            }
                        }
                    }

                    if (!bfound)
                    {
                        pw.println("BeamOnOffCmd\t" + beam_id + "\t" + time +
                            "\t0");
                        it.remove();
                    }
                }

                //  Paint the beams that remain.

                for (Radar r : pas)
                {
                    String beam_id = String.valueOf(this.getId()) + r.getId();

                    String color = "yellow";

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
                        color + "\t" + az_angle +
                        "\t" + el_angle + "\t" + distance);
                }

                for (Radar r : ras)
                {
                    String beam_id = String.valueOf(this.getId()) +
                        String.valueOf(r.getId());

                    String color = "blue";

                    Double3D aimpoint = r.getParent().getLocation();
                    double az_angle = CMWorld.getEarthModel().azimuthAngle(getParent().
                        getLocation(), aimpoint);
                    double el_angle = CMWorld.getEarthModel().elevationAngle(getParent().
                        getLocation(), aimpoint,
                        IEarthModel.EarthFactor.REAL_EARTH);
                    double distance = LengthUnit.NAUTICAL_MILES.convert(CMWorld.getEarthModel().
                        trueDistance(getParent().getLocation(), aimpoint),
                        LengthUnit.METERS);

                    pw.println("BeamData\t" + beam_id + "\t" +
                        String.valueOf(time) + "\t" + color + "\t" +
                        String.valueOf(az_angle) +
                        "\t" + String.valueOf(el_angle) + "\t" +
                        String.valueOf(distance));
                }

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }
}
