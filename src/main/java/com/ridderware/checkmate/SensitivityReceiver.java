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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A subclass of receiver that uses receiver sensitivity and radar parameters to
 * determine detection of radars.
 * @author Jeff Ridder
 */
public class SensitivityReceiver extends Receiver
{
    //  Receiver sensitivity
    private double sensitivity;

    //  Low and high frequency coverage of this receiver.
    //  We assume that the entire band is sampled every time
    //  scanReceiver is called.
    private double freq_low;

    private double freq_high;

    /**
     * Creates a new instance of SensitivityReceiver.
     * @param name name of the receiver.
     * @param world CMWorld in which the receiver plays.
     */
    public SensitivityReceiver(String name, CMWorld world)
    {
        super(name, world);

        this.createSensitivityReceiver();
    }

    /**
     * Sets the initial attributes of the receiver upon creation.
     */
    protected final void createSensitivityReceiver()
    {
        this.sensitivity = -20.;
        this.freq_low = 100.;
        this.freq_high = 18000.;
    }

    /**
     * Sets the initial attributes of the receiver before each simulation run.
     */
    @Override
    public void initialize()
    {
        super.initialize();
    }

    /**
     * Called by base class ScanReceiverBehavior to scan the receiver and make tracks.
     * @param current_time current time.
     */
    @Override
    public void scanReceiver(double current_time)
    {
        //  Loop over all radars, looking for those that meet the
        //  following criteria:
        //  1) Parent platform is ACTIVE
        //  2) Radar is emitting
        //  3) Radar frequency is between high and low frequency bounds
        //  4) signal strength exceeds sensitivity threshold

        getTracks().clear();

        for (Radar r : getWorld().getRadars())
        {
            if (r.getParent().getStatus() == Platform.Status.ACTIVE && getWorld().
                getEMLOSUtil().hasLOS(getParent(), r.getParent()))
            {
                if (r.isEmitting())
                {
                    if (r.getFrequency() >= getFreqLow() && r.getFrequency() <=
                        getFreqHigh())
                    {
                        //  Do signal strength calc && sensitivity test.
                        double distance = CMWorld.getEarthModel().
                            trueDistance(getParent().getLocation(), r.getParent().
                            getLocation());

                        double gain = r.getSidelobeGain() + getRNG().
                            nextGaussian() * 6.;

                        double signal =
                            DBUnit.dB(r.getPower()) + gain + 30. - 3. -
                            (21.98 +
                            2. *
                            DBUnit.dB(LengthUnit.NAUTICAL_MILES.convert(distance,
                            LengthUnit.METERS)) - 2. * DBUnit.dB(300. /
                            r.getFrequency()));

                        if (signal > getSensitivity())
                        {
                            getTracks().add(r);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the receiver sensitivity.
     * @return sensitivity in dBm.
     */
    public double getSensitivity()
    {
        return sensitivity;
    }

    /**
     * Sets the sensitivity of the receiver.
     * @param sensitivity sensitivity in dBm.
     */
    public void setSensitivity(double sensitivity)
    {
        this.sensitivity = sensitivity;
    }

    /**
     * Returns the low end the frequency coverage of the receiver.
     * @return low frequency in MHz.
     */
    public double getFreqLow()
    {
        return freq_low;
    }

    /**
     * Sets the low end of the frequency coverage.
     * @param freq_low low frequency in MHz.
     */
    public void setFreqLow(double freq_low)
    {
        this.freq_low = freq_low;
    }

    /**
     * Returns the high end of the frequency coverage.
     * @return high frequency in MHz.
     */
    public double getFreqHigh()
    {
        return freq_high;
    }

    /**
     * Sets the high end of the frequency coverage.
     * @param freq_high high frequency in MHz.
     */
    public void setFreqHigh(double freq_high)
    {
        this.freq_high = freq_high;
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

            if (child.getNodeName().toLowerCase().equals("sensitivity"))
            {
                this.sensitivity = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("freq-low"))
            {
                this.freq_low = Double.parseDouble(child.getTextContent());
            }
            else if (child.getNodeName().toLowerCase().equals("freq-high"))
            {
                this.freq_high = Double.parseDouble(child.getTextContent());
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

        Element e = document.createElement("sensitivity");
        e.setTextContent(String.valueOf(this.sensitivity));
        node.appendChild(e);

        e = document.createElement("freq-low");
        e.setTextContent(String.valueOf(this.freq_low));
        node.appendChild(e);

        e = document.createElement("freq-high");
        e.setTextContent(String.valueOf(this.freq_high));
        node.appendChild(e);
    }

    /**
     * Initializes the detection range ring for SIMDIS.  There is no 
     * range ring for the sensitivity receiver, so this is blank.
     *
     * @param asi_file SIMDIS .asi file to write to.
     */
    @Override
    public void simdisInitialize(File asi_file)
    {
    }

    /**
     * Writes the times series detection ring data to SIMDIS.  There is no
     * range ring for this receiver, so this is blank.
     * @param time time for which the write will occur.
     * @param asi_file SIMDIS .asi file to write to.
     */
    @Override
    public void simdisUpdate(double time, File asi_file)
    {
    }
}
