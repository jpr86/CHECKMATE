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
import java.util.Set;
import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.Space;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A receiver that uses a table of detection range versus emitter type to determine
 * detection of emitters.
 * @author Jeff Ridder
 */
public class TableLookupReceiver extends Receiver
{
    private HashMap<Integer, Double> detection_ranges =
        new HashMap<>();

    //  Map of classifications to the colors for which we will draw their detection ring.
    private HashMap<Integer, String> simdis_colors =
        new HashMap<>();

    private double[] range_array;

    /**
     * Creates a new instance of TableLookupReceiver
     * @param name name of the receiver.
     * @param world CMWorld in which the receiver plays.
     */
    public TableLookupReceiver(String name, CMWorld world)
    {
        super(name, world);

        this.createTableLookupReceiver();
    }

    /**
     * Sets the initial attributes of the receiver at time of creation.
     */
    protected final void createTableLookupReceiver()
    {

    }

    /**
     * Adds a radar classification / detection range pair to the lookup table.
     * @param classification radar classification.
     * @param range range at which radars of this type can be detected.
     */
    public void addDetectionRange(Integer classification, double range)
    {
        this.detection_ranges.put(classification, range);

        //  format the range_array.
        Set<Integer> classifications = detection_ranges.keySet();
        int max_i = Integer.MIN_VALUE;
        for (int i : classifications)
        {
            max_i = Math.max(i, max_i);
        }

        range_array = new double[max_i + 1];

        for (int i : classifications)
        {
            range_array[i] = detection_ranges.get(i);
        }
    }

    /**
     * Returns the detection range for a specified radar classification.
     *
     * @param classification classification of the radar.
     * @return detection range for that classification.
     */
    public double getDetectionRange(Integer classification)
    {
        return range_array[classification];
    }

    /**
     * Adds a SIMDIS color to paint the detection range ring for a given classification
     * in SIMDIS.
     * @param classification radar classification.
     * @param color SIMDIS color to paint the ring.
     */
    public void addSIMDISColor(Integer classification, String color)
    {
        this.simdis_colors.put(classification, color);
    }

    /**
     * Returns the SIMDIS color to paint the detection range ring for the given classification.
     * @param classification classification of the radar.
     * @return color to paint the detection range ring for this classification in SIMDIS.
     */
    public String getSIMDISColor(Integer classification)
    {
        return this.simdis_colors.get(classification);
    }

    /**
     * Called by base class ScanReceiverBehavior to sense the environment and make tracks.
     * @param current_time current time.
     */
    @Override
    public void scanReceiver(double current_time)
    {
        getTracks().clear();

        Double3D myLocation = getParent().getLocation();

        Set<Radar> radars = getWorld().getRadars();

        for (Radar r : radars)
        {
            int classification = r.getClassification();

            Platform r_parent = r.getParent();

            double range = range_array[classification];

            if (r_parent.getStatus() == Platform.Status.ACTIVE && range > 0. && getWorld().
                getEMLOSUtil().hasLOS(getParent(), r_parent))
            {
                if (r.isEmitting())
                {
                    //  Get distance, and check whether within range
                    if (CMWorld.getEarthModel().trueDistanceSq(myLocation,
                        r_parent.getLocation()) <= range * range)
                    {
                        getTracks().add(r);
                    }
                }
            }
        }
    }

    /**
     * Sets the system's attributes by parsing the XML.
     * @param node root node for the system.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();
        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().toLowerCase().equals("detection-range"))
            {
                Integer classification = null;
                Double range = null;
                String color = null;
                NodeList grandChildren = child.getChildNodes();
                for (int c = 0; c < grandChildren.getLength(); c++)
                {
                    Node grandChild = grandChildren.item(c);
                    if (grandChild.getNodeName().toLowerCase().equals("classification"))
                    {
                        classification =
                            Integer.parseInt(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().toLowerCase().equals("range"))
                    {
                        range = Double.parseDouble(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().equalsIgnoreCase("simdis-color"))
                    {
                        color = grandChild.getTextContent();
                    }
                }

                if (range == null || classification == null)
                {
                    throw new IllegalArgumentException(
                        child.getNodeName() + "] either range or " +
                        "classification were not specified");
                }
                else
                {
                    this.addDetectionRange(classification, range);
                    if (color != null)
                    {
                        this.addSIMDISColor(classification, color);
                    }
                }
            }
        }
    }

    /**
     * Creates XML nodes containing the system's attributes.
     *
     * @param node root node for the system.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        Set<Integer> keys = this.detection_ranges.keySet();

        for (Integer key : keys)
        {
            double detection_range = this.detection_ranges.get(key);

            e = document.createElement("detection-range");
            node.appendChild(e);

            Element f = document.createElement("classification");
            f.setTextContent(String.valueOf(key));
            e.appendChild(f);

            f = document.createElement("range");
            f.setTextContent(String.valueOf(detection_range));
            e.appendChild(f);

            String color = getSIMDISColor(key);
            if (color != null)
            {
                f = document.createElement("simdis-color");
                f.setTextContent(color);
                e.appendChild(f);
            }
        }
    }

    /**
     * Initializes the detection range rings for SIMDIS.
     *
     * @param asi_file SIMDIS .asi file to write to.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
        if (asi_file != null && getParent().getSIMDISIcon() != null &&
            this.getSIMDISDisplay())
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);

                //  I create one beam and multiple gates
                String parentID = String.valueOf(getParent().getId());
                String beamID = String.valueOf(this.getId());
                pw.println("BeamID\t" + parentID + "\t" + beamID);
                pw.println("VertBW\t" + beamID + "\t180.");
                pw.println("HorzBW\t" + beamID + "\t360.");
                pw.println("ElevOffset\t" + beamID + "\t90.");
                pw.println("AntennaAlgorithm\t" + beamID + "\t\"omni\"");
                pw.println("AntennaGain\t" + beamID + "\t30.");
                pw.println("AntennaPeakPower\t" + beamID + "\t200000.");
                pw.println("AntennaFrequency\t" + beamID + "\t400.");

                Set<Integer> keys = this.simdis_colors.keySet();

                for (Integer key : keys)
                {
                    String gateID = beamID + "999" + String.valueOf(key);

                    pw.println("GateID\t" + beamID + "\t" + gateID);
                    pw.println("GateType\t" + gateID + "\tCOVERAGE");
                }

                pw.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Writes the times series detection ring data to SIMDIS.
     * @param time time for which the write will occur.
     * @param asi_file SIMDIS .asi file to write to.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
        if (getParent().getSIMDISIcon() != null && asi_file != null &&
            this.getSIMDISDisplay() &&
            this.getScanReceiverBehavior().isEnabled())
        {
            try
            {
                FileWriter fw = new FileWriter(asi_file, true);
                PrintWriter pw = new PrintWriter(fw);
                String beamID = String.valueOf(this.getId());


                //  Write a beam and gate for every detection range in the table.
                pw.println("BeamOnOffCmd\t" + beamID + "\t" +
                    String.valueOf(time) + "\t1");

                Space s = getWorld().getSpace();
                double beamLength = CMWorld.getEarthModel().trueDistance(new Double3D(s.getXmin(), s.getYmin(), s.getZmin()),
                    new Double3D(s.getXmax(), s.getYmax(), s.getZmax()));

                pw.println("BeamData\t" + beamID + "\t" + time +
                    "\t0x00ff0000\t0.\t0.\t" +
                    LengthUnit.NAUTICAL_MILES.convert(beamLength,
                    LengthUnit.METERS));

                Set<Integer> keys = this.simdis_colors.keySet();

                for (Integer key : keys)
                {
                    String gateID = beamID + "999" + String.valueOf(key);
                    pw.println("GateOnOffCmd\t" + gateID + "\t" +
                        String.valueOf(time) + "\t1");

                    double detection_range =
                        LengthUnit.NAUTICAL_MILES.convert(this.detection_ranges.get(key),
                        LengthUnit.METERS);
                    pw.println("GateData\t" + gateID + "\t" +
                        String.valueOf(time) + "\t" + this.getSIMDISColor(key) +
                        "\t0.\t" + String.valueOf(-Math.PI / 1800.) + "\t" +
                        String.valueOf(1.999 * Math.PI) + "\t" +
                        String.valueOf(Math.PI / 900.) + "\t" +
                        String.valueOf(detection_range - 10.) + "\t" +
                        String.valueOf(detection_range + 10.) + "\t" +
                        String.valueOf(detection_range));
                }

                pw.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }
}
