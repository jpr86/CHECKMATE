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

import java.util.HashMap;
import com.ridderware.fuse.Double2D;
import org.apache.logging.log4j.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Class characterizing jamming assignments for AssignmentsJammer.
 * @author Jeff Ridder
 */
public class JammingAssignment implements Cloneable, IXML
{
    /**
     * Enumeration of type of jamming assignment.
     */
    public enum AssignmentType
    {
        /** Preemptive assignment */
        PREEMPTIVE,
        /** Reactive assignment */
        REACTIVE
    }

    //  Maps classification (key) to Double2D value holding (reference_effectiveness, reference range).
    private HashMap<Integer, Double2D> jamming_effectiveness =
        new HashMap<>();

    private AssignmentType assignment;

    private int resources_required;

    private final static Logger logger =
        LogManager.getLogger(JammingAssignment.class);

    /**
     * Creates a new instance of JammingAssignment.
     */
    public JammingAssignment()
    {
        this.assignment = AssignmentType.PREEMPTIVE;

        this.resources_required = 1;
    }

    /**
     * Adds a new radar type covered by this assignment.
     * @param radar_classification radar classification covered by this assignment.
     * @param reference_effectiveness the reference effectiveness (between 0 and 1, 1 being perfect).
     * @param reference_range The range corresponding to the reference effectiveness.
     */
    public void addCoverage(int radar_classification,
        double reference_effectiveness, double reference_range)
    {
        this.jamming_effectiveness.put(radar_classification,
            new Double2D(reference_effectiveness, reference_range));
    }

    /**
     * Returns a java Integer array containing the classifications of the radars
     * covered by this assignment.
     * @return java Integer array.
     */
    public Integer[] getRadarsCovered()
    {
        Integer[] radars = new Integer[jamming_effectiveness.size()];
        this.jamming_effectiveness.keySet().toArray(radars);
        return radars;
    }

    /**
     * Returns whether the input radar is covered by this jammer.
     * @param tgt a Radar object.
     * @return true (covered) or false (not).
     */
    public boolean isCovered(Radar tgt)
    {
        return jamming_effectiveness.containsKey(tgt.getClassification());
    }

    /**
     * Returns a Double2D object containing the reference effectiveness (X) and reference
     * range (Y) for the specified radar.
     * @param tgt a Radar object.
     * @return Double2D containing (reference effectiveness, reference range).
     */
    public Double2D getCoverage(Radar tgt)
    {
        return jamming_effectiveness.get(tgt.getClassification());
    }

    /**
     * Sets the type of assignment -- PREEMPTIVE or REACTIVE.
     * @param assignment assignment type.
     */
    public void setAssignmentType(AssignmentType assignment)
    {
        this.assignment = assignment;
    }

    /**
     * Returns the type of assignment.
     * @return PREEMPTIVE or REACTIVE.
     */
    public AssignmentType getAssignmentType()
    {
        return assignment;
    }

    /**
     * Sets the number of jamming resources required by this assignment.
     *
     * @param resources_required number of jamming resources required.
     */
    public void setResourcesRequired(int resources_required)
    {
        this.resources_required = resources_required;
    }

    /**
     * Returns the number of jamming resources required by this assignment.
     * @return number of jamming resources required.
     */
    public int getResourcesRequired()
    {
        return this.resources_required;
    }

    /**
     * Method to safely clone the assignment.
     * @return clone of the assignment.
     */
    @Override
    public JammingAssignment clone()
    {
        JammingAssignment obj = null;
        try
        {
            obj = (JammingAssignment) super.clone();
        }
        catch (CloneNotSupportedException ex)
        {
        }

        return obj;
    }

    /**
     * Parses the XML node to set the attributes of this object.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        if (node.getNodeName().equalsIgnoreCase("jamming-assignment"))
        {
            this.assignment = AssignmentType.valueOf(node.getAttributes().
                getNamedItem("type").getTextContent());
        }
        else
        {
            logger.error("Incorrect node passed to fromXML");
            System.exit(1);
        }

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("coverage"))
            {
                //  Parse child nodes to get classification, eff, and ref-range
                NodeList attrs = child.getChildNodes();
                Integer classification = null;
                Double refRange = null, effectiveness = null;
                for (int m = 0; m < attrs.getLength(); m++)
                {
                    Node attr = attrs.item(m);
                    if (attr.getNodeName().toLowerCase().equals("classification"))
                    {
                        classification = Integer.parseInt(attr.getTextContent());
                    }
                    else if (attr.getNodeName().toLowerCase().equals("reference-range"))
                    {
                        refRange = Double.parseDouble(attr.getTextContent());
                    }
                    else if (attr.getNodeName().toLowerCase().equals("effectiveness"))
                    {
                        effectiveness =
                            Double.parseDouble(attr.getTextContent());
                    }
                }

                if (effectiveness != null && classification != null &&
                    refRange != null)
                {
                    addCoverage(classification, effectiveness, refRange);
                }
            }
            else if (child.getNodeName().equalsIgnoreCase("resources-required"))
            {
                this.setResourcesRequired(Integer.parseInt(child.getTextContent()));
            }
        }
    }

    /**
     * Writes this object's attributes to an XML node.
     * @param node Node to write the attributes to.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        Element e;

        //  Create elephants
        e = document.createElement("resources-required");
        e.setTextContent(String.valueOf(this.resources_required));
        node.appendChild(e);

        Integer[] radars = getRadarsCovered();
        for (int i : radars)
        {
            e = document.createElement("coverage");
            node.appendChild(e);

            Element f = document.createElement("classification");
            f.setTextContent(String.valueOf(i));
            e.appendChild(f);

            f = document.createElement("reference-range");
            f.setTextContent(String.valueOf(jamming_effectiveness.get(i).getY()));
            e.appendChild(f);

            f = document.createElement("effectiveness");
            f.setTextContent(String.valueOf(jamming_effectiveness.get(i).getX()));
            e.appendChild(f);
        }
    }
}
