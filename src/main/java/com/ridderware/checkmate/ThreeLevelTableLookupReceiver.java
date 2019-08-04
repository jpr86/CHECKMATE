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
 * A receiver that can detect any one of the three features of a DirectionalRadar
 * (mainbeam, sidelobe, average sidelobe), but at different ranges for each.  This receiver
 * takes into account the orientation of the radar antenna relative to the receiver.  If the
 * radar is not a DirectionalRadar, then the third detection range value is used (corresponding
 * to average sidelobe).
 *
 * @author Jeff Ridder
 */
public class ThreeLevelTableLookupReceiver extends Receiver
{
    private HashMap<Integer, DetectionRanges> detection_ranges =
        new HashMap<>();

    //  Map of classifications to the colors for which we will draw their detection ring.
    private HashMap<Integer, String> simdis_colors =
        new HashMap<>();

    //  Map of classifications to the ranges at which we will draw their detection rings.
    private HashMap<Integer, Double> simdis_ranges =
        new HashMap<>();

    private DetectionRanges[] range_array;

    /**
     * Creates a new instance of ThreeLevelTableLookupReceiver
     * @param name name of the receiver.
     * @param world CMWorld in which the receiver plays.
     */
    public ThreeLevelTableLookupReceiver(String name, CMWorld world)
    {
        super(name, world);

        this.createThreeLevelTableLookupReceiver();
    }

    /**
     * Sets the initial attributes of the receiver at time of creation.
     */
    protected final void createThreeLevelTableLookupReceiver()
    {
    }

    /**
     * Adds a radar classification / detection-range-set pair to the lookup table.
     * @param classification radar classification.
     * @param ml_range range at which mainlobes of radars of this type can be detected.
     * @param sl_range range at which sidelobes of radars of this type can be detected.
     * @param ave_sl_range range at which average sidelobes of radars of this type can be detected.
     */
    public void addDetectionRanges(int classification, double ml_range,
        double sl_range, double ave_sl_range)
    {
        this.detection_ranges.put(classification, new DetectionRanges(ml_range,
            sl_range, ave_sl_range));

        //  format the range_array.
        Set<Integer> classifications = detection_ranges.keySet();
        int max_i = Integer.MIN_VALUE;
        for (int i : classifications)
        {
            max_i = Math.max(i, max_i);
        }

        range_array = new DetectionRanges[max_i + 1];

        for (int i : classifications)
        {
            range_array[i] = detection_ranges.get(i);
        }
    }

    /**
     * Returns the mainlobe detection range for a specified radar classification.
     *
     * @param classification classification of the radar.
     * @return mainlobe detection range for that classification.
     */
    public double getMainlobeDetectionRange(int classification)
    {
        double range = -1;

        if (this.range_array[classification] != null)
        {
            range = this.range_array[classification].getMainlobeDetectionRange();
        }

        return range;
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
     * Adds a SIMDIS range to paint the detection range ring for a given classification
     * in SIMDIS.
     * @param classification radar classification.
     * @param range range at which to paint the ring.
     */
    public void addSIMDISRange(Integer classification, Double range)
    {
        this.simdis_ranges.put(classification, range);
    }

    /**
     * Returns the SIMDIS range to paint the detection range ring for the given classification.
     * @param classification classification of the radar.
     * @return range of the ring as painted in SIMDIS.
     */
    public Double getSIMDISRange(Integer classification)
    {
        return this.simdis_ranges.get(classification);
    }

    /**
     * Returns the sidelobe detection range for a specified radar classification.
     *
     * @param classification classification of the radar.
     * @return sidelobe detection range for that classification.
     */
    public double getSidelobeDetectionRange(int classification)
    {
        double range = -1;

        if (this.range_array[classification] != null)
        {
            range = this.range_array[classification].getSidelobeDetectionRange();
        }

        return range;
    }

    /**
     * Returns the average sidelobe detection range for a specified radar classification.
     *
     * @param classification classification of the radar.
     * @return average sidelobe detection range for that classification.
     */
    public double getAverageSidelobeDetectionRange(int classification)
    {
        double range = -1;

        if (this.range_array[classification] != null)
        {
            range =
                this.range_array[classification].
                getAverageSidelobeDetectionRange();
        }

        return range;
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

            //  Need to first determine which lobe we're in, and then pull the appropriate detection range for that

            if (r_parent.getStatus() == Platform.Status.ACTIVE && r.isEmitting() && getWorld().
                getEMLOSUtil().hasLOS(r_parent, getParent()))
            {
                double distance =
                    CMWorld.getEarthModel().trueDistance(
                    r_parent.getLocation(),
                    myLocation);

                DetectionRanges drange = null;
                if (range_array != null)
                {
                    drange = range_array[classification];
                }

                double range = 0.;
                if (drange != null && distance <=
                    drange.getMainlobeDetectionRange())
                {
                    switch (this.getAntennaLobe(r))
                    {
                        case 0:
                        {
                            //  mainlobe
                            range = drange.getMainlobeDetectionRange();
                            break;
                        }
                        case 1:
                        {
                            //  sidelobe
                            range = drange.getSidelobeDetectionRange();
                            break;
                        }
                        case 2:
                        default:
                        {
                            //  average sidelobe
                            range = drange.getAverageSidelobeDetectionRange();
                        }
                    }
                }

                if (distance <= range)
                {
                    //  Get distance, and check whether within range
                    getTracks().add(r);
                }
            }
        }
    }

    /**
     * Method to determine which lobe of a radar antenna the receiver is in based on
     * its orientation.  By default, if the radar is omnidirectional (e.g., a
     * Radar object) then this routine returns 2 indicating the average sidelobe.
     * If the radar is directional (e.g., a DirectionalRadar object), then this
     * routine will return 0 if the receiver is in the mainlobe, 1 for the sidelobe,
     * and 2 for the average sidelobe.
     * @param tgt target radar
     * @return 0 for mainlobe, 1 for sidelobe, 2 for average sidelobe.
     */
    public int getAntennaLobe(Radar tgt)
    {
        int lobe = 2;

        if (tgt instanceof DirectionalRadar)
        {
            DirectionalRadar r = (DirectionalRadar) tgt;
            double two_pi = 2. * Math.PI;
            if (r.getAntenna() != null)
            {
                Antenna a = r.getAntenna();
                Double3D my_location = this.getParent().getLocation();
                Double3D radar_location = r.getParent().getLocation();

                //  First, gotta update the antenna to my time.
                r.moveAntenna(getUniverse().getCurrentTime());

                //  Get angle theta and phi between radar and jammer, and compare to the antenna boresight.
                double theta =
                    CMWorld.getEarthModel().azimuthAngle(radar_location,
                    my_location);
                double phi =
                    CMWorld.getEarthModel().elevationAngle(radar_location,
                    my_location, IEarthModel.EarthFactor.EM_EARTH);

                //  Get az and el angles relative to antenna boresight
                double az = theta - a.getBoresight().getX();
                double el = Math.abs(phi - a.getBoresight().getY());

                az = (az > Math.PI ? az - two_pi : az);
                az = (az < -Math.PI ? az + two_pi : az);

                az = Math.abs(az);

                if (az <= r.getAntenna().getMainlobeAz() && el <= r.getAntenna().
                    getMainlobeEl())
                {
                    //  We're in the mainbeam
                    lobe = 0;
                }
                else if (az <= r.getAntenna().getSidelobeAz() && el <= r.
                    getAntenna().
                    getSidelobeEl())
                {
                    //  We're in the first sidelobe
                    lobe = 1;
                }
            }
        }
        return lobe;
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
                Double ml_range = null;
                Double sl_range = null;
                Double ave_sl_range = null;
                String color = null;
                Double simdis_range = null;
                NodeList grandChildren = child.getChildNodes();
                for (int c = 0; c < grandChildren.getLength(); c++)
                {
                    Node grandChild = grandChildren.item(c);
                    if (grandChild.getNodeName().toLowerCase().equals(
                        "classification"))
                    {
                        classification =
                            Integer.parseInt(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().toLowerCase().equals(
                        "mainlobe-range"))
                    {
                        ml_range =
                            Double.parseDouble(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().toLowerCase().equals(
                        "sidelobe-range"))
                    {
                        sl_range =
                            Double.parseDouble(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().toLowerCase().equals(
                        "ave-sidelobe-range"))
                    {
                        ave_sl_range =
                            Double.parseDouble(grandChild.getTextContent());
                    }
                    else if (grandChild.getNodeName().equalsIgnoreCase(
                        "simdis-color"))
                    {
                        color = grandChild.getTextContent();
                    }
                    else if (grandChild.getNodeName().equalsIgnoreCase(
                        "simdis-range"))
                    {
                        simdis_range =
                            Double.parseDouble(grandChild.getTextContent());
                    }
                }

                if (ml_range == null || classification == null || sl_range ==
                    null || ave_sl_range == null)
                {
                    throw new IllegalArgumentException(
                        child.getNodeName() + "] either range or " +
                        "classification were not specified");
                }
                else
                {
                    this.addDetectionRanges(classification, ml_range, sl_range,
                        ave_sl_range);

                    if (color != null)
                    {
                        this.addSIMDISColor(classification, color);
                    }

                    if (simdis_range != null)
                    {
                        this.addSIMDISRange(classification, simdis_range);
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
            DetectionRanges detection_range = this.detection_ranges.get(key);

            e = document.createElement("detection-range");
            node.appendChild(e);

            Element f = document.createElement("classification");
            f.setTextContent(String.valueOf(key));
            e.appendChild(f);

            f = document.createElement("mainlobe-range");
            f.setTextContent(String.valueOf(detection_range.
                getMainlobeDetectionRange()));
            e.appendChild(f);

            f = document.createElement("sidelobe-range");
            f.setTextContent(String.valueOf(detection_range.
                getSidelobeDetectionRange()));
            e.appendChild(f);

            f = document.createElement("ave-sidelobe-range");
            f.setTextContent(String.valueOf(detection_range.
                getAverageSidelobeDetectionRange()));
            e.appendChild(f);

            String color = getSIMDISColor(key);
            if (color != null)
            {
                f = document.createElement("simdis-color");
                f.setTextContent(color);
                e.appendChild(f);
            }

            Double simdis_range = getSIMDISRange(key);
            if (simdis_range != null)
            {
                f = document.createElement("simdis-range");
                f.setTextContent(simdis_range.toString());
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
                double beamLength = CMWorld.getEarthModel().trueDistance(
                    new Double3D(s.getXmin(), s.getYmin(), s.getZmin()),
                    new Double3D(s.getXmax(), s.getYmax(), s.getZmax()));

                pw.println("BeamData\t" + beamID + "\t" + time +
                    "\t0x00ff0000\t0.\t0.\t" +
                    LengthUnit.NAUTICAL_MILES.convert(beamLength,
                    LengthUnit.METERS));

                Set<Integer> keys = this.simdis_colors.keySet();

                for (Integer key : keys)
                {
                    String gateID = beamID + "999" + String.valueOf(key);
                    pw.println("GateOnOffCmd\t" + gateID + "\t" + time + "\t1");

                    Double simdis_range = this.simdis_ranges.get(key);
                    if (simdis_range == null)
                    {
                        simdis_range =
                            this.getAverageSidelobeDetectionRange(key);
                    }

                    simdis_range =
                        LengthUnit.NAUTICAL_MILES.convert(simdis_range,
                        LengthUnit.METERS);

                    pw.println("GateData\t" + gateID + "\t" + time + "\t" +
                        this.getSIMDISColor(key) + "\t0.\t" +
                        String.valueOf(-Math.PI / 1800.) + "\t" +
                        String.valueOf(1.999 * Math.PI) + "\t" +
                        String.valueOf(Math.PI / 900.) + "\t" +
                        String.valueOf(simdis_range - 10.) + "\t" +
                        String.valueOf(simdis_range + 10.) + "\t" +
                        String.valueOf(simdis_range));
                }

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    /**
     * Inner class for the three detection ranges associated with each radar classification.
     */
    protected class DetectionRanges
    {
        private double ml_range;

        private double sl_range;

        private double ave_sl_range;

        /**
         * Creates a new instance of DetectionRanges.
         * @param mb_range the mainlobe detection range.
         * @param sl_range the sidelobe detection range.
         * @param ave_sl_range the average sidelobe detection range.
         */
        public DetectionRanges(double mb_range, double sl_range,
            double ave_sl_range)
        {
            this.ml_range = mb_range;
            this.sl_range = sl_range;
            this.ave_sl_range = ave_sl_range;
        }

        /**
         * Returns the mainlobe detection range.
         * @return mainlobe detection range.
         */
        public double getMainlobeDetectionRange()
        {
            return this.ml_range;
        }

        /**
         * Returns the sidelobe detection range.
         * @return sidelobe detection range.
         */
        public double getSidelobeDetectionRange()
        {
            return this.sl_range;
        }

        /**
         * Returns the average sidelobe detection range.
         * @return average sidelobe detection range.
         */
        public double getAverageSidelobeDetectionRange()
        {
            return this.ave_sl_range;
        }
    }
}
