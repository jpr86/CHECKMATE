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
import java.util.HashMap;
import java.util.Map;
import com.ridderware.fuse.Agent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Abstract base class for all CHECKMATE agents.
 *
 * @author Jeff Ridder
 */
public abstract class CMAgent extends Agent implements IXML
{
    private static int last_id = 0;

    private int id;

    private CMWorld world;

    /**
     * Creates a new instance of CMAgent.
     * @param name name of the agent.
     * @param world CMWorld in which this agent plays.
     */
    public CMAgent(String name, CMWorld world)
    {
        super(name);
        this.world = world;

        this.generateId();
    }

    private void generateId()
    {
        id = ++last_id;
        if (this.world != null)
        {
            while (!this.world.addId(id, this))
            {
                id = ++last_id;
            }
        }
    }

    /**
     * Called by the World to inform the agent that its ID has been superceded by
     * another agent and to generate a new Id.
     */
    public void invalidateId()
    {
        //  ID is no longer valid.  Must generate new.
        this.getWorld().removeId(this.id);
        this.generateId();
    }

    /**
     * Returns the agent's unique ID.
     * @return id.
     */
    public int getId()
    {
        return this.id;
    }

    /**
     * Sets the unique ID of the agent, overriding the ID that is automatically
     * generated for each agent.  You may wish to override the auto-generated ID so
     * that hierarchies of superior/subordinate may be established in scenario files.
     * @param id unique ID number to set.
     * @return the ID of the agent which, if successful, will be the same as the input parameter.
     */
    public int setId(int id)
    {
        while (!getWorld().addId(id, this))
        {
            getWorld().getAgent(id).invalidateId();
        }

        getWorld().removeId(this.id);
        this.id = id;

        return this.id;
    }

    /**
     * Returns the CMWorld in which the agent plays.
     * @return CMWorld object.
     */
    public CMWorld getWorld()
    {
        return this.world;
    }

    /**
     * Resets the agent's attributes before each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();
    }

    /**
     * Sets the name of the agent.
     * @param name name.
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * Something throw together quickly to turn a String into a Color. Handles all
     * standard {@code java.awt.Colors} (ie. red, blue, green, etc). Also, handles
     * rgb strings in the form {@code r,g,b} ex: {@code 255,0,128}
     *
     * @author Jason C. HandUber, jhanduber &ltat&gt gmail &ltdot&gt com
     */
    protected enum ColorzEnum
    {
        /**
         * Enum definition of white.
         */
        White(Color.WHITE, "white"),
        /**
         * Enum definition of light gray.
         */
        LightGray(Color.LIGHT_GRAY, "light gray"),
        /**
         * Enum definition of gray.
         */
        Gray(Color.GRAY, "gray"),
        /**
         * Enum definition of dark gray.
         */
        DarkGray(Color.DARK_GRAY, "dark gray"),
        /**
         * Enum definition of black.
         */
        Black(Color.BLACK, "black"),
        /**
         * Enum definition of Red.
         */
        Red(Color.RED, "red"),
        /**
         * Enum definition of pink.
         */
        Pink(Color.PINK, "pink"),
        /**
         * Enum definition of orange.
         */
        Orange(Color.ORANGE, "orange"),
        /**
         * Enum definition of yellow.
         */
        Yellow(Color.YELLOW, "yellow"),
        /**
         * Enum definition of green.
         */
        Green(Color.GREEN, "green"),
        /**
         * Enum definition of magenta.
         */
        Magenta(Color.MAGENTA, "magenta"),
        /**
         * Enum definition of cyan.
         */
        Cyan(Color.CYAN, "cyan"),
        /**
         * Enum definition of blue.
         */
        Blue(Color.BLUE, "blue");

        private final static Map<String, Color> name2color =
            new HashMap<String, Color>();

        static
        {
            for (ColorzEnum color : ColorzEnum.values())
            {
                name2color.put(color.name, color.color);
            }
        }

        private final String name;

        private final Color color;

        private ColorzEnum(Color color, String colorName)
        {
            this.name = colorName;
            this.color = color;
        }

        /**
         * Returns true if the color can be retrieved from the input string.
         * @param color string describing the color.
         * @return true if the color can be retrieved.
         */
        public static boolean canGetColor(String color)
        {
            return (getColor(color) != null);
        }

        /**
         * Returns the specified color.
         * @param color string describing the color.
         * @return the corresponding Java Color object.
         */
        public static Color getColor(String color)
        {
            int periodIndex = color.indexOf(".");
            String periodParse = null;
            if (periodIndex != -1)
            {
                periodParse = color.substring(periodIndex + 1, color.length());
            }

            final Color colour;

            if (name2color.containsKey(color))
            {
                colour = name2color.get(color);
            }
            else if (periodParse != null)
            {
                colour = name2color.get(color.substring(periodIndex + 1,
                    color.length()));
            }
            else if (color.split(",").length == 3)
            {
                String[] split = color.split(",");
                int r = Integer.parseInt(split[0]);
                int g = Integer.parseInt(split[1]);
                int b = Integer.parseInt(split[2]);
                colour = new Color(r, g, b);
            }
            else
            {
                colour = null;
            }
            return colour;
        }
    }

    /**
     * Parses the input XML node, extracting attributes for the agent.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);
            if (child.getNodeName().equalsIgnoreCase("name"))
            {
                this.setName(child.getTextContent());
            }
            else if (child.getNodeName().equalsIgnoreCase("id"))
            {
                this.setId(Integer.parseInt(child.getTextContent()));
            }
        }
    }

    /**
     * Creates subordinate elements for the node containing the agent's attributes.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        Document document = node.getOwnerDocument();

        Element e = document.createElement("name");
        e.setTextContent(this.name);
        node.appendChild(e);

        e = document.createElement("id");
        e.setTextContent(String.valueOf(this.id));
        node.appendChild(e);
    }
}
