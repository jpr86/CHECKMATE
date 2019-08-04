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

import com.ridderware.fuse.MutableDouble2D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A basic antenna class.  This antenna can scan only circularly or to track a target
 * for a target tracking radar. It has three basic lobes:  mainbeam, sidelobe, and average sidelobe.
 *
 * @author Jeff Ridder
 */
public class Antenna extends CMAgent
{
    //  Mainlobe in dB relative to isotropic and extent angles relative to boresight in radians.
    private double mainlobe;

    private double ml_az;

    private double ml_el;

    //  First sidelobe gain in dB relative to isotropic & extent angles relative to boresight
    private double sidelobe;

    private double sl_az;

    private double sl_el;

    //  Average sidelobe gain in dB relative to isotropic
    private double ave_sidelobe;

    //  Orientation of the boresight angle (az, el), where az is relative to north, and el
    //  is relative to the horizon.
    private MutableDouble2D boresight;

    /**
     * Creates a new instance of Antenna.
     * @param name name of the antenna.
     * @param world CMWorld in which this agent plays.
     */
    public Antenna(String name, CMWorld world)
    {
        super(name, world);

        this.setMainlobe(0.);
        this.setMainlobeAz(0.);
        this.setMainlobeEl(0.);
        this.setSidelobe(0.);
        this.setSidelobeAz(0.);
        this.setSidelobeEl(0.);
        this.setAveSidelobe(0.);
        this.boresight = new MutableDouble2D(0., 0.);
    }

    /**
     * Returns the mainlobe gain in dBi.
     * @return mainlobe gain.
     */
    public double getMainlobe()
    {
        return mainlobe;
    }

    /**
     * Sets the mainlobe gain in dBi.
     * @param mainlobe mainlobe gain in dBi.
     */
    public void setMainlobe(double mainlobe)
    {
        this.mainlobe = mainlobe;
    }

    /**
     * Returns azimuth mainlobe extent from boresight (i.e., 1/2 az beamwidth) in radians.
     * @return azimuth mainlobe extent in radians.
     */
    public double getMainlobeAz()
    {
        return ml_az;
    }

    /**
     * Sets the azimuth mainlobe extent from boresight in radians (1/2 az beamwidth).
     * @param ml_az azimuth mainlobe extent in radians.
     */
    public void setMainlobeAz(double ml_az)
    {
        this.ml_az = ml_az;
    }

    /**
     * Returns elevation mainlobe extent from boresight (i.e., 1/2 el beamwidth) in radians.
     * @return elevation mainlobe extent in radians.
     */
    public double getMainlobeEl()
    {
        return ml_el;
    }

    /**
     * Sets the elevation mainlobe extent from boresight in radians (1/2 el beamwidth).
     * @param ml_el elevation mainlobe extent in radians.
     */
    public void setMainlobeEl(double ml_el)
    {
        this.ml_el = ml_el;
    }

    /**
     * Returns the first sidelobe gain in dBi.
     * @return first sidelobe gain in dBi.
     */
    public double getSidelobe()
    {
        return sidelobe;
    }

    /**
     * Sets the first sidelobe gain in dBi.
     * @param sidelobe first sidelobe gain in dBi.
     */
    public void setSidelobe(double sidelobe)
    {
        this.sidelobe = sidelobe;
    }

    /**
     * Returns the azimuth extent of the first sidelobe from boresight in radians.
     * @return azimuth extent of first sidelobe in radians.
     */
    public double getSidelobeAz()
    {
        return sl_az;
    }

    /**
     * Sets the azimuth extent of the first sidelobe from boresight in radians.
     * @param sl_az azimuth extent of first sidelobe in radians.
     */
    public void setSidelobeAz(double sl_az)
    {
        this.sl_az = sl_az;
    }

    /**
     * Returns the elevation extent of the first sidelobe from boresight in radians.
     * @return elevation extent of first sidelobe in radians.
     */
    public double getSidelobeEl()
    {
        return sl_el;
    }

    /**
     * Sets the elevation extent of the first sidelobe from boresight in radians.
     * @param sl_el elevation extent of first sidelobe in radians.
     */
    public void setSidelobeEl(double sl_el)
    {
        this.sl_el = sl_el;
    }

    /**
     * Returns the average sidelobe gain in dBi.  This value will be used everywhere
     * outside of the first sidelobe and mainlobe.
     * @return average sidelobe in dBi.
     */
    public double getAveSidelobe()
    {
        return ave_sidelobe;
    }

    /**
     * Sets the average sidelobe gain in dBi.
     * @param ave_sidelobe average sidelobe gain.
     */
    public void setAveSidelobe(double ave_sidelobe)
    {
        this.ave_sidelobe = ave_sidelobe;
    }

    /**
     * Sets the boresight angle of the antenna.  Azimuth is specified relative
     * to North (y axis) and Elevation is specified relative to the horizon.  Both are
     * in radians.
     * @param az azimuth angle relative to North in radians.
     * @param el elevation angle relative to North in radians.
     */
    public void setBoresight(double az, double el)
    {
        this.boresight.setXY(az, el);
    }

    /**
     * Returns the boresight angle of the antenna.
     * @return boresight angle (az, el).
     */
    public MutableDouble2D getBoresight()
    {
        return this.boresight;
    }

    /**
     * Sets the antenna's attributes by parsing the XML.
     * @param node root node for the antenna.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeName().equalsIgnoreCase("mainlobe-gain"))
            {
                this.setMainlobe(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("mainlobe-az"))
            {
                this.setMainlobeAz(Math.toRadians(Double.parseDouble(child.getTextContent())));
            }
            else if (child.getNodeName().equalsIgnoreCase("mainlobe-el"))
            {
                this.setMainlobeEl(Math.toRadians(Double.parseDouble(child.getTextContent())));
            }
            else if (child.getNodeName().toLowerCase().equals("sidelobe-gain"))
            {
                this.setSidelobe(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("sidelobe-az"))
            {
                this.setSidelobeAz(Math.toRadians(Double.parseDouble(child.getTextContent())));
            }
            else if (child.getNodeName().equalsIgnoreCase("sidelobe-el"))
            {
                this.setSidelobeEl(Math.toRadians(Double.parseDouble(child.getTextContent())));
            }
            else if (child.getNodeName().equalsIgnoreCase("ave-sidelobe-gain"))
            {
                this.setAveSidelobe(Double.parseDouble(child.getTextContent()));
            }
        }
    }

    /**
     * Creates XML nodes containing the antenna's attributes.
     *
     * @param node root node for the antenna.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        e = document.createElement("mainlobe-gain");
        e.setTextContent(String.valueOf(this.getMainlobe()));
        node.appendChild(e);

        e = document.createElement("mainlobe-az");
        e.setTextContent(String.valueOf(Math.toDegrees(this.getMainlobeAz())));
        node.appendChild(e);

        e = document.createElement("mainlobe-el");
        e.setTextContent(String.valueOf(Math.toDegrees(this.getMainlobeEl())));
        node.appendChild(e);

        e = document.createElement("sidelobe-gain");
        e.setTextContent(String.valueOf(this.getSidelobe()));
        node.appendChild(e);

        e = document.createElement("sidelobe-az");
        e.setTextContent(String.valueOf(Math.toDegrees(this.getSidelobeAz())));
        node.appendChild(e);

        e = document.createElement("sidelobe-el");
        e.setTextContent(String.valueOf(Math.toDegrees(this.getSidelobeEl())));
        node.appendChild(e);

        e = document.createElement("ave-sidelobe-gain");
        e.setTextContent(String.valueOf(this.getAveSidelobe()));
        node.appendChild(e);
    }
}
