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
import com.ridderware.fuse.Double3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A subclass of Receiver that implements cookie-cutter detection.  That is, all
 * emitters are detected at the same range.
 * @author Jeff Ridder
 */
public class CookieCutterReceiver extends Receiver
{
    private double detection_range;

    private String simdis_color;

    /**
     * Creates a new instance of CookieCutterReceiver.
     * @param name name of the receiver.
     * @param world CMWorld in which the receiver plays.
     */
    public CookieCutterReceiver(String name, CMWorld world)
    {
        super(name, world);

        this.createCookieCutterReceiver();
    }

    /**
     * Sets the initial attributes of the receiver upon creation.
     */
    protected final void createCookieCutterReceiver()
    {
        this.setDetectionRange(10.);
        this.simdis_color = "0x6000ff00";
    }

    /**
     * Called by the base class ScanReceiverBehavior to tell the receiver to scan
     * the environment.
     * @param current_time current time.
     */
    @Override
    public void scanReceiver(double current_time)
    {
        getTracks().clear();

        Double3D myLocation = getParent().getLocation();

        double dr_sq = detection_range * detection_range;

        for (Radar r : getWorld().getRadars())
        {
            Platform r_parent = r.getParent();

            if (r_parent.getStatus() == Platform.Status.ACTIVE && getWorld().
                getEMLOSUtil().hasLOS(getParent(), r_parent))
            {
                if (r.isEmitting())
                {
                    Double3D r_loc = r_parent.getLocation();

                    double dsq =
                        CMWorld.getEarthModel().trueDistanceSq(myLocation,
                        r_loc);

                    if (dsq <= dr_sq)
                    {
                        getTracks().add(r);
                    }
                }
            }
        }
    }

    /**
     * Sets the color to draw the detection range ring in SIMDIS.
     * @param color Color string using a SIMDIS compatible value (either color string or hex).
     */
    public void setSIMDISColor(String color)
    {
        this.simdis_color = color;
    }

    /**
     * Returns the color to draw the detection range ring in SIMDIS.
     * @return color.
     */
    public String getSIMDISColor()
    {
        return this.simdis_color;
    }

    /**
     * Returns the cookie-cutter detection range of the receiver.
     * @return detection range in nautical miles.
     */
    public double getDetectionRange()
    {
        return detection_range;
    }

    /**
     * Sets the cookie-cutter detection range of the receiver.
     * @param detection_range detection range in nautical miles.
     */
    public void setDetectionRange(double detection_range)
    {
        this.detection_range = detection_range;
    }

    /**
     * Sets the parameters of the receiver by parsing the XML.
     * @param node root XML node for the receiver.
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
                this.detection_range =
                    Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().equalsIgnoreCase("simdis-display"))
            {
                this.setSIMDISColor(child.getAttributes().getNamedItem("color").
                    getTextContent());
            }
        }
    }

    /**
     * Creates DOM XML nodes containing the receiver's parameters.
     * @param node root XML node for the receiver.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("detection-range");
        e.setTextContent(String.valueOf(this.detection_range));
        node.appendChild(e);

        NodeList chillens = node.getChildNodes();
        for (int j = 0; j < chillens.getLength(); j++)
        {
            Node n = chillens.item(j);
            if (n.getNodeName().equalsIgnoreCase("simdis-display"))
            {
                if (n instanceof Element)
                {
                    Element ne = (Element) n;
                    ne.setAttribute("color", this.getSIMDISColor());
                }
            }
        }
    }

    /**
     * Initializes the detection range ring for SIMDIS.
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

                String parentID = String.valueOf(getParent().getId());
                String beamID = String.valueOf(this.getId());
                String gateID = beamID + "9991";
                pw.println("BeamID\t" + parentID + "\t" + beamID);
                pw.println("VertBW\t" + beamID + "\t180.");
                pw.println("HorzBW\t" + beamID + "\t360.");
                pw.println("ElevOffset\t" + beamID + "\t90.");
                pw.println("AntennaAlgorithm\t" + beamID + "\t\"omni\"");
                pw.println("AntennaGain\t" + beamID + "\t30.");
                pw.println("AntennaPeakPower\t" + beamID + "\t200000.");
                pw.println("AntennaFrequency\t" + beamID + "\t400.");
                pw.println("GateID\t" + beamID + "\t" + gateID);
                pw.println("GateType\t" + gateID + "\tCOVERAGE");

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
                String gateID = beamID + "9991";

                pw.println("BeamOnOffCmd\t" + beamID + "\t" +
                    time + "\t1");
                pw.println("GateOnOffCmd\t" + gateID + "\t" +
                    time + "\t1");

                double dr_meters =
                    LengthUnit.NAUTICAL_MILES.convert(detection_range,
                    LengthUnit.METERS);

                pw.println("BeamData\t" + beamID + "\t" +
                    time + "\t" + "0x00ff0000" + "\t0.\t0.\t" + dr_meters);

                pw.println("GateData\t" + gateID + "\t" +
                    time + "\t" + this.getSIMDISColor() + "\t0.\t" + -Math.PI /
                    1800. + "\t" + 1.999 * Math.PI + "\t" + Math.PI / 900. +
                    "\t" + String.valueOf(dr_meters - 10.) + "\t" +
                    String.valueOf(dr_meters + 10.) + "\t" + dr_meters);

                pw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }
}
