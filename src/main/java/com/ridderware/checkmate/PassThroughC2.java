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
import java.awt.Font;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import com.ridderware.fuse.Behavior;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A command and control class that simply forwards radar tracks to upper echelon
 * with a time delay.
 *
 * @author Jeff Ridder
 */
public class PassThroughC2 extends UncertainLocationPlatform implements IRadarTrackReceiver
{
    //  Min and mean times for processing and forwarding tracks to upper echelon.
    private double min_delay;

    private double mean_delay;

    //  Time at which tracks will be removed if not updated.
    private double age_out;

    //  The maximum number of tracks handled by this C2.  New tracks will be
    //  ignored if they will exceed the track_capacity.
    private int track_capacity;

    //  Map from the platform to the track data concerning the platform.
    private HashMap<Platform, TrackData> track_map =
        new HashMap<>();

    //  This is just a convenient place to stuff dropped tracks until they can be forwarded to upper
    //  echelon.
    private HashSet<Platform> dropped_tracks = new HashSet<>();

    /**
     * Creates a new instance of PassThroughC2
     * @param name name of the object.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     */
    public PassThroughC2(String name, CMWorld world, int points)
    {
        super(name, world, points);
        this.createPassThroughC2();
    }

    /**
     * Creates a new instance of PassThroughC2 with attributes for GUI display.
     * @param name name of the object.
     * @param world CMWorld in which it plays.
     * @param points arbitrary point value.
     * @param ttf font used to draw the object.
     * @param fontSymbol font symbol to display.
     * @param color color to draw the symbol.
     */
    public PassThroughC2(String name, CMWorld world, int points, Font ttf,
        String fontSymbol, Color color)
    {
        super(name, world, points, ttf, fontSymbol, color);
        this.createPassThroughC2();
    }

    /**
     * Sets the initial, default attributes of the object.
     */
    protected final void createPassThroughC2()
    {
        this.min_delay = 0.;
        this.mean_delay = 0.;

        this.age_out = 60.;
        this.track_capacity = 10;

        //  Should run continuously.  No need to keep a pointer to it.
        this.addBehavior(new ProcessTracksBehavior());
    }

    /**
     * Sets the minimum delay for processing tracks.
     * @param min_delay min delay in seconds.
     */
    public void setMinDelay(double min_delay)
    {
        this.min_delay = min_delay;
    }

    /**
     * Returns the minimum delay for processing tracks.
     * @return min track processing delay.
     */
    public double getMinDelay()
    {
        return this.min_delay;
    }

    /**
     * Called by the simulation framework to reset the object prior to 
     * each simulation run.
     */
    @Override
    public void reset()
    {
        super.reset();

        this.track_map.clear();
        this.dropped_tracks.clear();
    }

    /**
     * Sets the mean dealy for processing tracks.
     * 
     * @param mean_delay mean delay in seconds.
     */
    public void setMeanDelay(double mean_delay)
    {
        this.mean_delay = mean_delay;
    }

    /**
     * Returns the mean delay for processing tracks.
     * @return mean delay in seconds.
     */
    public double getMeanDelay()
    {
        return this.mean_delay;
    }

    /**
     * Sets the age out time for tracks.  Tracks that haven't been updated
     * for this amount of time are automatically dropped.
     * @param age_out age out time in seconds.
     */
    public void setAgeOut(double age_out)
    {
        this.age_out = age_out;
    }

    /**
     * Returns the age out time for tracks.
     * @return age out time in seconds.
     */
    public double getAgeOut()
    {
        return this.age_out;
    }

    /**
     * Sets the maximum number of tracks that this object can handle.  More
     * tracks than this will be ignored.
     * @param capacity track capacity.
     */
    public void setTrackCapacity(int capacity)
    {
        this.track_capacity = capacity;
    }

    /**
     * Returns the maximum number of tracks that this object can handle.
     * @return track capacity.
     */
    public int getTrackCapacity()
    {
        return this.track_capacity;
    }

    /**
     * Returns the track data pertaining to the specified platform.
     * @param track Platform for which track data is requested.
     * @return a TrackData object, or null if none exists.
     */
    public TrackData getTrackData(Platform track)
    {
        return this.track_map.get(track);
    }

    /**
     * Returns the set of all Platforms currently being tracked.
     * @return Set containing all active tracks.
     */
    public Set<Platform> getTracks()
    {
        return this.track_map.keySet();
    }

    /**
     * Called by subordinates to report active tracks.
     * @param radar Radar reporting the tracks, or null if reporting agent is not a radar.
     * @param tracks Set of tracks being reported.
     */
    @Override
    public void reportActiveTracks(Radar radar, Set<Platform> tracks)
    {
        //  Reporting tracks to this C2 has the effect of adding/updating tracks
        //  up to the limits of the track_capacity.
        for (Platform p : tracks)
        {
            if (this.track_map.containsKey(p))
            {
                TrackData d = track_map.get(p);

                d.updateTrackData(getUniverse().getCurrentTime(), radar);
            }
            else
            {
                if (this.track_map.size() < this.track_capacity)
                {
                    TrackData d = new TrackData(p,
                        getUniverse().getCurrentTime(), radar);
                    this.track_map.put(p, d);
                }
            }
        }
    }

    /**
     * Called by subordinates to report dropped tracks.
     * @param radar Radar reporting the drops, or null if reporting agent is not a radar.
     * @param dropped_tracks Set of dropped tracks being reported.
     */
    @Override
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks)
    {
        for (Platform p : dropped_tracks)
        {
            if (this.track_map.containsKey(p))
            {
                TrackData d = track_map.get(p);

                if (d.ew_radar == radar)
                {
                    d.ew_radar = null;
                }

                if (d.ta_radar == radar)
                {
                    d.ta_radar = null;
                }

                if (d.tt_radar == radar)
                {
                    d.tt_radar = null;
                }

                if (radar == null ||
                    (d.ew_radar == null && d.ta_radar == null && d.tt_radar ==
                    null))
                {
                    track_map.remove(p);
                    this.dropped_tracks.add(p);
                }
            }
        }
    }

    /**
     * Called periodically using the specified delay to process the tracks.  For this
     * class, processTracks ages out old tracks and reports dropped and active tracks to
     * a superior.
     * @param time current simulation time.
     */
    public void processTracks(double time)
    {
        //  Called by periodic behavior

        //  First age out old tracks
        Set<Platform> keys = this.track_map.keySet();
        Iterator<Platform> it = keys.iterator();
        while (it.hasNext())
        {
            Platform p = it.next();
            TrackData d = this.track_map.get(p);
            if (d.update_time - time > this.age_out)
            {
                it.remove();
            }
        }

        //  Now the dropped and active tracks
        if (this.getSuperior() instanceof IRadarTrackReceiver)
        {
            if (!this.dropped_tracks.isEmpty())
            {
                ((IRadarTrackReceiver) this.getSuperior()).reportDroppedTracks(null,
                    this.dropped_tracks);
                this.dropped_tracks.clear();    //  ready to accumulate more.
            }
            keys = this.track_map.keySet();
            if (!keys.isEmpty())
            {
                ((IRadarTrackReceiver) this.getSuperior()).reportActiveTracks(null,
                    keys);
            }
        }
    }

    /**
     * A behavior to periodically call the processTracks method.
     *
     * @author Jeff Ridder
     */
    protected class ProcessTracksBehavior extends Behavior
    {
        /**
         * Returns the next scheduled time to perform the behavior.
         * @param current_time current simulation time.
         * @return next time to perform behavior.
         */
        @Override
        public double getNextScheduledTime(double current_time)
        {
            double next_time = current_time;
            while (next_time <= current_time)
            {
                next_time += min_delay - Math.log(1. - getRNG().nextDouble()) *
                    (mean_delay - min_delay);
            }

            return next_time;
        }

        /**
         * Performs the behavior.
         * @param current_time current simulation time.
         */
        @Override
        public void perform(double current_time)
        {
            processTracks(current_time);
        }
    }

    /**
     * Parses the input XML node, setting the attributes of the object.
     * @param node XML node.
     */
    @Override
    public void fromXML(Node node)
    {
        super.fromXML(node);

        NodeList children = node.getChildNodes();

        for (int j = 0; j < children.getLength(); j++)
        {
            Node child = children.item(j);

            if (child.getNodeName().equalsIgnoreCase("delay"))
            {
                this.setMinDelay(Double.parseDouble(child.getAttributes().
                    getNamedItem("min").getTextContent()));
                this.setMeanDelay(Double.parseDouble(child.getAttributes().
                    getNamedItem("mean").getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("age-out"))
            {
                this.setAgeOut(Double.parseDouble(child.getTextContent()));
            }
            else if (child.getNodeName().equalsIgnoreCase("track-capacity"))
            {
                this.setTrackCapacity(Integer.parseInt(child.getTextContent()));
            }
        }
    }

    /**
     * Writes the object's attributes as sub-elements of the specified XML node.
     * @param node XML node.
     */
    @Override
    public void toXML(Node node)
    {
        super.toXML(node);

        Document document = node.getOwnerDocument();

        Element e;

        //  Create element
        e = document.createElement("delay");
        e.setAttribute("min", String.valueOf(this.min_delay));
        e.setAttribute("mean", String.valueOf(this.mean_delay));
        node.appendChild(e);

        e = document.createElement("age-out");
        e.setTextContent(String.valueOf(this.age_out));
        node.appendChild(e);

        e = document.createElement("track-capacity");
        e.setTextContent(String.valueOf(this.track_capacity));
        node.appendChild(e);
    }

    /**
     * Class to hold the track data for a track.
     * 
     * @author Jeff Ridder
     */
    protected class TrackData
    {
        /** Platform being tracked */
        public Platform track;

        /** Last update time of the track */
        public double update_time;

        /** Last early warning radar to report an update */
        public Radar ew_radar = null;

        /** Last target acquisition radar to report an update */
        public Radar ta_radar = null;

        /** Last target tracking radar to report an update */
        public Radar tt_radar = null;

        /**
         * Creates a new instance of TrackData.
         * @param track Platform being tracked.
         * @param update_time current time.
         * @param radar radar reporting the track.
         */
        public TrackData(Platform track, double update_time, Radar radar)
        {
            this.track = track;
            this.updateTrackData(update_time, radar);
        }

        /**
         * Updates the track data.
         * @param update_time update time.
         * @param radar radar reporting the update.
         */
        public void updateTrackData(double update_time, Radar radar)
        {
            this.update_time = update_time;

            Radar.Function function = Radar.Function.EW;

            if (radar != null)
            {
                function = radar.getFunction();
            }

            switch (function)
            {
                case EW:
                    ew_radar = radar;
                    break;
                case TA:
                    ta_radar = radar;
                    break;
                case TT:
                    tt_radar = radar;
                    break;
                default:
                    ew_radar = radar;
            }
        }
    }
}
