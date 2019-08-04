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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An extension of the CHECKMATE RoutePoint that adds bank angle to the attributes.  The bank angle is
 * the absolute value of the angle used by an aircraft in executing a turn after flying through the route point
 * in order to change its heading to the next route point.
 *
 * @author Jeff Ridder
 */
public class BankedRoutePoint extends RoutePoint
{
    //  The bank angle in radians.
    private double bank_angle;
    //  Slop in n.mi. -- a platform that flies within this distance of the point has achieved that point.
    private double slop;

    /** Creates a new instance of BankedRoutePoint */
    public BankedRoutePoint()
    {
        this.bank_angle = 0.;
        this.slop = 0.;
    }

    /**
     * Returns the bank angle after turning from this route point.
     *
     * @return bank angle in radians.
     */
    public double getBankAngle()
    {
        return bank_angle;
    }

    /**
     * Sets the point's attributes by parsing the XML.
     *
     * @param node root node for the route point.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeName().equalsIgnoreCase("bank-angle"))
            {
                this.bank_angle =
                    Math.toRadians(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("slop"))
            {
                this.slop = Double.parseDouble(child.getTextContent());
            }
        }
    }

    /**
     * Creates XML nodes containing the points attributes.
     *
     * @param node root node for the point.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e = document.createElement("bank-angle");
        e.setTextContent(String.valueOf(Math.toDegrees(bank_angle)));
        node.appendChild(e);

        e = document.createElement("slop");
        e.setTextContent(String.valueOf(slop));
        node.appendChild(e);
    }

    /**
     * Returns the slop factor.  Slop is the distance from this point that counts as achieving the point.
     * @return slop factor in nautical miles.
     */
    public double getSlop()
    {
        return slop;
    }
}
