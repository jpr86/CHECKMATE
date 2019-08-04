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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import com.ridderware.fuse.Double3D;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Terrain model that stores coarse and fine terrain grids in memory.  This is a port
 *  of the ARES terrain model, which does a fast first pass using coarse terrain to determine
 *  whether LOS definitely exists, definitely does not exist, or could exist.  If it is
 *  the latter condition, then the model uses fine terrain to determine LOS.
 *
 * @author Jeff Ridder
 */
public class TwoLevelTerrain implements ITerrainModel, IXML
{
    //
    //  Terrain data
    //
    //  High resolution elevation data (in meters)
    private short[] fineElevation;

    //  Low resolution elevation data (in meters) -- lowest elevation in a coarse grid cell.
    private short[] coarseElevationMin;

    //  Low resolution elevation data (in meters) -- highest elevation in a coarse grid cell.
    private short[] coarseElevationMax;

    //  Coarse and fine resolution
    private int xPointsCoarse;

    //  longitude for LLA
    private int yPointsFine;

    //  latitude for LLA
    private int xPointsFine;

    private double coarseRes;

    //  Lat res for LLA
    private double xFineRes;

    //  Long res for LLA
    private double yFineRes;

    //  The spatial domain covered by the terrain grid.  These must be in the
    //  same units as used for platform locations (i.e., n.mi. for "ENU" and radians for "LLA").
    //  Min lat
    private double xMin = Double.MAX_VALUE;

    //  Max lat
    private double xMax = Double.MIN_VALUE;

    //  Min long
    private double yMin = Double.MAX_VALUE;

    //  Max long
    private double yMax = Double.MIN_VALUE;

    /**
     *  Creates a new instance of TwoLevelTerrain
     */
    public TwoLevelTerrain()
    {
        fineElevation = null;
    }

    /**
     *  Returns the terrain elevation at the specified point.  This uses the fine
     *  terrain grid.
     *
     * @param  x x-coordinate in n.mi. (ENU) or lat radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or long radians (LLA)
     * @return terrain elevation in meters
     */
    @Override
    public double elevation(double x, double y)
    {
        double el = 0.;
        if (x >= xMin && x <= xMax && y >= yMin && y <= yMax)
        {
            int i = terrainCell(x, y);

            el = (double) fineElevation[i];
        }

        return el;
    }

    /**
     * Returns the index of the terrain cell covering the specified point.
     * This is useful for platforms to determine when they need to recalculate or invalidate LOS to other platforms.
     * @param  x x-coordinate in n.mi. (ENU) or lat radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or long radians (LLA)
     * @return terrain cell index (or -1 if not applicable or outside terrain bounds).
     */
    @Override
    public int terrainCell(double x, double y)
    {
        int i = -1;
        if (fineElevation != null)
        {
            int yIndex = (int) ((y + 0.5 * yFineRes - yMin) / yFineRes);
            int xIndex = (int) ((x + 0.5 * xFineRes - xMin) / xFineRes);

            i = yIndex * xPointsFine + xIndex;

            if (i < 0 || i >= fineElevation.length)
            {
                i = -1;
            }
        }

        return i;
    }

    /**
     * Returns the min coarse grid elevation at the specified point.
     * @param  x
     * @param  y
     * @return terrain elevation in meters
     */
    private double minCoarseElevation(double x, double y)
    {
        double el = 0.;
        if (x >= xMin && x <= xMax && y >= yMin && y <= yMax)
        {
            //  We may wish to store these two as member variables.
//            double xRes = (xMax - xMin)/colsCoarse;
//            double yRes = (yMax - yMin)/rowsCoarse;

//            int yIndex = (int) ((y + 0.5 * coarseRes - yMin) / coarseRes);
//            int xIndex = (int) ((x + 0.5 * coarseRes - xMin) / coarseRes);
            int yIndex = (int) Math.floor((y - yMin) / coarseRes);
            int xIndex = (int) Math.floor((x - xMin) / coarseRes);

            int i = yIndex * xPointsCoarse + xIndex;

            el = (double) coarseElevationMin[i];
        }

        return el;
    }

    /**
     * Returns the max coarse grid elevation at the specified point.
     * @param  x
     * @param  y
     * @return terrain elevation in meters
     */
    private double maxCoarseElevation(double x, double y)
    {
        double el = 0.;
        if (x >= xMin && x <= xMax && y >= yMin && y <= yMax)
        {
            //  We may wish to store these two as member variables.
//            double xRes = (xMax - xMin)/colsCoarse;
//            double yRes = (yMax - yMin)/rowsCoarse;

//            int yIndex = (int) ((y + 0.5 * coarseRes - yMin) / coarseRes);
//            int xIndex = (int) ((x + 0.5 * coarseRes - xMin) / coarseRes);
            int yIndex = (int) Math.floor((y - yMin) / coarseRes);
            int xIndex = (int) Math.floor((x - xMin) / coarseRes);

            int i = yIndex * xPointsCoarse + xIndex;

            el = (double) coarseElevationMax[i];
        }

        return el;
    }

    /**
     *  Check for unobstructed Line Of Sight between two points on earth.
     *
     * @param  location1    Location to check line of sight from.
     * @param  location2    Location to check line of sight to.
     * @param  k            Earth Factor.
     * @return true if Line Of Sight is unobstructed, false otherwise
     */
    @Override
    public boolean hasLOS(Double3D location1, Double3D location2,
        IEarthModel.EarthFactor k)
    {
        boolean los = true;

        if (CMWorld.getEarthModel().getCoordinateSystem().equalsIgnoreCase(
            "LLA"))
        {
            los = roundEarthLOS(location1, location2, k);
        }
        else if (CMWorld.getEarthModel().getCoordinateSystem().
            equalsIgnoreCase("ENU"))
        {
            los = flatEarthLOS(location1, location2);
        }
        return los;
    }

    /**
     *  Check for unobstructed Line Of Sight between two points on a round earth.
     *
     * @param  location1    Location to check line of sight from.
     * @param  location2    Location to check line of sight to.
     * @param  k            Earth Factor.
     * @return true if Line Of Sight is unobstructed, false otherwise
     */
    private boolean roundEarthLOS(Double3D location1, Double3D location2,
        IEarthModel.EarthFactor k)
    {
        boolean los = true;

        double alpha = 0.;
        double terrain_elevation = 0.;
        if (CMWorld.getEarthModel() instanceof RoundEarth)
        {
            alpha = ((RoundEarth) CMWorld.getEarthModel()).gcAlpha(location1,
                location2);
        }

        if (alpha > 0.)
        {
            double distance = CMWorld.getEarthModel().trueDistance(location1,
                location2);

            //  Read off the coords for efficiency.
            double x1 = location1.getX();
            double y1 = location1.getY();
            double z1 = location1.getZ();
            double x2 = location2.getX();
            double y2 = location2.getY();
            double z2 = location2.getZ();

            //  Find the fine terrain indices for 1 and 2.
            int i1 = (int) ((x1 + 0.5 * xFineRes - xMin) / xFineRes);
            int j1 = (int) ((y1 + 0.5 * yFineRes - yMin) / yFineRes);

            int i2 = (int) ((x2 + 0.5 * xFineRes - xMin) / xFineRes);
            int j2 = (int) ((y2 + 0.5 * yFineRes - yMin) / yFineRes);

            //	Next question:  Is there terrain between 1 and 2?
            if ((i1 < 0 && i2 < 0) ||
                (j1 < 0 && j2 < 0) ||
                (i1 >= xPointsFine && i2 >= xPointsFine) ||
                (j1 >= yPointsFine && j2 >= yPointsFine))
            {
                //  No terrain between us.  Use radar horizon to figure out LOS
                if (distance >= 82.89750 * Math.sqrt(k.value()) *
                    (Math.sqrt(z1) + Math.sqrt(z2)))
                {
                    los = false;
                }
            }
            else
            {
                //  There IS terrain between us...figure it out.

                //	This is the interesting case.  Do as follows:
                //	1) Using coarse terrain, find the max terrain elevation between us
                //	2) Use second derivative of LOS elevation between us to determine
                //	   the point of closest approach (a') of the LOS vector to the smooth earth.
                //	   Ensure that a' is between 0 and gc_alpha by the following:
                //	      a' = __max(0, a').  a' = __min(gc_alpha, a')
                //	3) solve for h' = h(a'), then:
                //         a) If h' < local terrain_elevation, then bLOS = false
                //	   b) If h' > max_terrain_elevation (from coarse grid), then bLOS = true
                //	   c) If neither a) nor b), the LOS is ambiguous...proceed to step 4).
                //	4) If LOS is ambiguous at this point, then iterate a from a' one terrain
                //	   cell at a time (in both directions) until either:
                //	   a) h = h(a) < local terrain_elevation, then bLOS = false
                //	   b) h > max_terrain_elevation (from coarse grid), then bLOS = true
                //	   c) a = 0 or gc_alpha, then bLOS = true
                //

                //
                //	Step 1) Using coarse terrain, find the max and min terrain elevation between 1 and 2
                //

                //  Develop an estimate of the number of coarse cells between the points.  This is not
                //  exact and does not need to be exact.
                int number_of_coarse_cells = (int) Math.ceil(alpha / coarseRes);
                double coarse_delta_alpha = alpha / number_of_coarse_cells;

                double rk = RoundEarth.EARTH_RADIUS_NMI * k.value();
                double rh1 = rk + z1;

                double coslat1 = Math.cos(x1);
                double sinlat1 = Math.sin(x1);
                double bearing =
                    CMWorld.getEarthModel().azimuthAngle(location1, location2);
                double elevation_angle = CMWorld.getEarthModel().
                    elevationAngle(location1, location2, k);
                double cosbearing = Math.cos(bearing);
                double half_pi = Math.PI / 2.;

                double max_terrain_elevation = Double.NEGATIVE_INFINITY;
                double min_terrain_elevation = Double.POSITIVE_INFINITY;
                int i;
                for (i = 0; i <= number_of_coarse_cells; i++)
                {
                    double local_alpha = i * coarse_delta_alpha;

                    //	Find the terrain location of the current local_alpha...lat and long, then i and j
                    double arg1 =
                        Math.cos(Math.abs(local_alpha + x1 - half_pi)) - (1. -
                        cosbearing) * coslat1 * Math.sin(local_alpha);
                    double local_lat = Math.asin(Math.max(Math.min(arg1, 1.),
                        -1.));

                    double dellong = 0.;
                    double part1 = (Math.cos(local_alpha) - sinlat1 *
                        Math.sin(local_lat));
                    part1 *= 1. / coslat1 / Math.cos(local_lat);
                    part1 = Math.max(Math.min(part1, 1.), -1.);
                    part1 = Math.abs(Math.acos(part1));
                    dellong = part1;

                    double local_long = 0;
                    if (bearing >= Math.PI)
                    {
                        local_long = y1 - dellong;
                    }
                    else
                    {
                        local_long = y1 + dellong;
                    }

                    double terrain_el =
                        maxCoarseElevation(local_lat, local_long);
                    max_terrain_elevation = Math.max(max_terrain_elevation,
                        terrain_el);

                    terrain_el = minCoarseElevation(local_lat, local_long);
                    min_terrain_elevation = Math.min(min_terrain_elevation,
                        terrain_el);
                }

                //
                //	Step 2) Use second derivative of LOS elevation between us to determine
                //	   the point of closest approach (a') of the LOS vector to the smooth earth.
                //	   Ensure that a' is between 0 and alpha by the following:
                //	   a' = max(0, a').  a' = min(alpha, a')
                //
                double alpha_prime = Math.max(0, Math.min(alpha,
                    -elevation_angle * k.value()));

                //	Step 3) solve for h' = h(a'), then:
                //         a) If h' < local terrain_elevation, then bLOS = false
                //	   b) If h' > max_terrain_elevation (from coarse grid), then bLOS = true
                //	   c) If neither a) nor b), the LOS is ambiguous...proceed to step 4).

                double los_elevation =
                    LengthUnit.NAUTICAL_MILES.convert(rh1 * Math.sin(half_pi +
                    elevation_angle) /
                    Math.sin(half_pi - elevation_angle - alpha_prime / k.value()) -
                    rk, LengthUnit.METERS);

                //	Find the terrain location of the current local_alpha...lat and long, then i and j
                double arg1 = Math.cos(Math.abs(alpha_prime + x1 - half_pi)) -
                    (1. - cosbearing) * coslat1 * Math.sin(alpha_prime);
                double local_lat = Math.asin(Math.max(Math.min(arg1, 1.), -1.));

                double dellong = 0.;
                double part1 = (Math.cos(alpha_prime) - sinlat1 *
                    Math.sin(local_lat));
                part1 *= 1. / coslat1 / Math.cos(local_lat);
                part1 = Math.max(Math.min(part1, 1.), -1.);
                part1 = Math.abs(Math.acos(part1));
                dellong = part1;

                double local_long = 0;
                if (bearing >= Math.PI)
                {
                    local_long = y1 - dellong;
                }
                else
                {
                    local_long = y1 + dellong;
                }

                terrain_elevation = elevation(local_lat, local_long);

                if (los_elevation < terrain_elevation)
                {
                    //	Case a) above
                    los = false;
                }
                else if (los_elevation > max_terrain_elevation)
                {
                    //	Case b) above
                    los = true;
                }
                else
                {
                    //	Case c) ambiguous

                    //	4) If LOS is ambiguous at this point, then iterate a from a' one terrain
                    //	   cell at a time (in both directions) until either:
                    //	   a) h = h(a) < local terrain_elevation, then los = false
                    //	   b) h > max_terrain_elevation (from coarse grid), then los = true
                    //	   c) a = 0 or alpha, then los = true

                    int number_of_cells =
                        (int) Math.sqrt((i2 - i1) * (i2 - i1) + (j2 - j1) *
                        (j2 - j1));
                    double delta_alpha = alpha / number_of_cells;

                    double local_alpha = alpha_prime + delta_alpha;
                    while (local_alpha <= alpha && los_elevation <
                        max_terrain_elevation && los == true)
                    {
                        //	Find the terrain location of the current local_alpha...lat and long, then i and j
                        double arg2 = Math.cos(Math.abs(local_alpha + x1 -
                            half_pi)) - (1. - cosbearing) * coslat1 *
                            Math.sin(local_alpha);
                        local_lat = Math.asin(Math.max(Math.min(arg2, 1.), -1.));

                        dellong = 0.;

                        part1 = (Math.cos(local_alpha) - sinlat1 *
                            Math.sin(local_lat));
                        part1 *= 1. / coslat1 / Math.cos(local_lat);
                        part1 = Math.max(Math.min(part1, 1.), -1.);
                        part1 = Math.abs(Math.acos(part1));
                        dellong = part1;

                        local_long = 0;
                        if (bearing >= Math.PI)
                        {
                            local_long = y1 - dellong;
                        }
                        else
                        {
                            local_long = y1 + dellong;
                        }

                        terrain_elevation = elevation(local_lat, local_long);

                        los_elevation = LengthUnit.NAUTICAL_MILES.convert(rh1 *
                            Math.sin(half_pi + elevation_angle) /
                            Math.sin(half_pi - elevation_angle - local_alpha /
                            k.value()) - rk, LengthUnit.METERS);

                        if (los_elevation < terrain_elevation)
                        {
                            los = false;
                        }

                        local_alpha += delta_alpha;
                    }

                    local_alpha = alpha_prime - delta_alpha;
                    while (local_alpha >= 0. && los_elevation <
                        max_terrain_elevation && los == true)
                    {
                        //	Find the terrain location of the current local_alpha...lat and long, then i and j
                        double arg2 = Math.cos(Math.abs(local_alpha + x1 -
                            half_pi)) - (1. - cosbearing) * coslat1 *
                            Math.sin(local_alpha);
                        local_lat = Math.asin(Math.max(Math.min(arg2, 1.), -1.));

                        dellong = 0.;

                        part1 = (Math.cos(local_alpha) - sinlat1 *
                            Math.sin(local_lat));
                        part1 *= 1. / coslat1 / Math.cos(local_lat);
                        part1 = Math.max(Math.min(part1, 1.), -1.);
                        part1 = Math.abs(Math.acos(part1));
                        dellong = part1;

                        local_long = 0;
                        if (bearing >= Math.PI)
                        {
                            local_long = y1 - dellong;
                        }
                        else
                        {
                            local_long = y1 + dellong;
                        }

                        terrain_elevation = elevation(local_lat, local_long);

                        los_elevation = LengthUnit.NAUTICAL_MILES.convert(rh1 *
                            Math.sin(half_pi + elevation_angle) /
                            Math.sin(half_pi - elevation_angle - local_alpha /
                            k.value()) - rk, LengthUnit.METERS);

                        if (los_elevation < terrain_elevation)
                        {
                            los = false;
                        }

                        local_alpha -= delta_alpha;
                    }
                }
            }
        }
        return los;
    }

    /**
     *  Check for unobstructed Line Of Sight between two points on a flat earth.
     *
     * @param  location1    Location to check line of sight from.
     * @param  location2    Location to check line of sight to.
     * @param  k            Earth Factor.
     * @return true if Line Of Sight is unobstructed, false otherwise
     */
    private boolean flatEarthLOS(Double3D location1, Double3D location2)
    {
        boolean los = true;

        //  TODO:  Need to adapt this for Flat earth.
        //  For flat Earth, the concept of alpha can be replaced by distance.
        double distance = CMWorld.getEarthModel().trueDistance(location1,
            location2);

        double terrain_elevation = 0.;

        if (distance > 0.)
        {
            //  Read off the coords for efficiency.
            double x1 = location1.getX();
            double y1 = location1.getY();
            double z1 = location1.getZ();
            double x2 = location2.getX();
            double y2 = location2.getY();
            double z2 = location2.getZ();

            //  Find the fine terrain indices for 1 and 2.
            int i1 = (int) ((x1 + 0.5 * xFineRes - xMin) / xFineRes);
            int j1 = (int) ((y1 + 0.5 * yFineRes - yMin) / yFineRes);

            int i2 = (int) ((x2 + 0.5 * xFineRes - xMin) / xFineRes);
            int j2 = (int) ((y2 + 0.5 * yFineRes - yMin) / yFineRes);

            //	Next question:  Is there terrain between 1 and 2?
            if ((i1 < 0 && i2 < 0) ||
                (j1 < 0 && j2 < 0) ||
                (i1 >= xPointsFine && i2 >= xPointsFine) ||
                (j1 >= yPointsFine && j2 >= yPointsFine))
            {
                //  No terrain between us.  Use radar horizon to figure out LOS
                if (distance >= 95.72179262 * (Math.sqrt(z1) + Math.sqrt(z2)))
                {
                    los = false;
                }
            }
            else
            {
                //  There IS terrain between us...figure it out.

                //	This is the interesting case.  Do as follows:
                //	1) Using coarse terrain, find the max terrain elevation between us
                //	2) Start at the point (1 or 2) with the lowest height.
                //	3) solve for h' = h(a'), then:
                //         a) If h' < local terrain_elevation, then los = false
                //	   b) If h' > max_terrain_elevation (from coarse grid), then los = true
                //	   c) If neither a) nor b), the los is ambiguous...proceed to step 4).
                //	4) If LOS is ambiguous at this point, then iterate a from a' one terrain
                //	   cell at a time (in both directions) until either:
                //	   a) h = h(a) < local terrain_elevation, then bLOS = false
                //	   b) h > max_terrain_elevation (from coarse grid), then bLOS = true
                //	   c) a = 0 or distance, then los = true
                //

                //
                //	Step 1) Using coarse terrain, find the max and min terrain elevation between 1 and 2
                //

                //  Develop an estimate of the number of coarse cells between the points.  This is not
                //  exact and does not need to be exact.
                int number_of_coarse_cells = (int) Math.ceil(distance /
                    coarseRes);
                double coarse_delta_dist = distance / number_of_coarse_cells;

                double max_terrain_elevation = Double.NEGATIVE_INFINITY;
                double min_terrain_elevation = Double.POSITIVE_INFINITY;
                int i;
                for (i = 0; i <= number_of_coarse_cells; i++)
                {
                    double local_dist = i * coarse_delta_dist;

                    Double3D local_pt = CMWorld.getEarthModel().
                        interpolateLocation(location1, location2, local_dist);

                    double terrain_el = maxCoarseElevation(local_pt.getX(),
                        local_pt.getY());
                    max_terrain_elevation = Math.max(max_terrain_elevation,
                        terrain_el);

                    terrain_el = minCoarseElevation(local_pt.getX(),
                        local_pt.getY());
                    min_terrain_elevation = Math.min(min_terrain_elevation,
                        terrain_el);
                }

                //
                //	Step 2) Use second derivative of LOS elevation between us to determine
                //	   the point of closest approach (a') of the LOS vector to the smooth earth.
                //	   Ensure that a' is between 0 and alpha by the following:
                //	   a' = max(0, a').  a' = min(alpha, a')
                //
                Double3D test_pt = location1.getZ() < location2.getZ() ? location1
                    : location2;
                Double3D far_pt;
                if (test_pt.equals(location1))
                {
                    far_pt = location2;
                }
                else
                {
                    far_pt = location1;
                }

                //	Step 3) solve for h' = h(a'), then:
                //         a) If h' < local terrain_elevation, then bLOS = false
                //	   b) If h' > max_terrain_elevation (from coarse grid), then bLOS = true
                //	   c) If neither a) nor b), the LOS is ambiguous...proceed to step 4).

                double h_prime =
                    LengthUnit.NAUTICAL_MILES.convert(test_pt.getZ(),
                    LengthUnit.METERS);

                terrain_elevation = elevation(test_pt.getX(), test_pt.getY());

                if (h_prime < terrain_elevation)
                {
                    //	Case a) above
                    los = false;
                }
                else if (h_prime > max_terrain_elevation)
                {
                    //	Case b) above
                    los = true;
                }
                else
                {
                    //	Case c) ambiguous

                    //	4) If LOS is ambiguous at this point, then iterate a from a' one terrain
                    //	   cell at a time (in both directions) until either:
                    //	   a) h = h(a) < local terrain_elevation, then los = false
                    //	   b) h > max_terrain_elevation (from coarse grid), then los = true
                    //	   c) a = 0 or alpha, then los = true

                    int number_of_cells =
                        (int) Math.sqrt((i2 - i1) * (i2 - i1) + (j2 - j1) *
                        (j2 - j1));
                    double delta_dist = distance / number_of_cells;

                    double local_dist = delta_dist;
                    while (local_dist <= distance && h_prime <
                        max_terrain_elevation && los == true)
                    {
                        //	Find the terrain location of the current local_alpha...lat and long, then i and j
                        Double3D interp_pt = CMWorld.getEarthModel().
                            interpolateLocation(test_pt, far_pt, local_dist);

                        terrain_elevation = elevation(interp_pt.getX(),
                            interp_pt.getY());

                        h_prime =
                            LengthUnit.NAUTICAL_MILES.convert(interp_pt.getZ(),
                            LengthUnit.METERS);

                        if (h_prime < terrain_elevation)
                        {
                            los = false;
                        }

                        local_dist += delta_dist;
                    }
                }
            }
        }
        return los;
    }

    /**
     * Loads terrain from the specified array and determines all necessary terrain parameters.
     *
     * @param terrain_data an array of fine resolution terrain elevation data in meters above sea level.
     * @param xMin the x-coordinate of the left boundary of the terrain box.  This is in n.mi. for ENU and radians longitude for LLA.
     * @param xMax the x-coordinate of the right boundary of the terrain box.  This is in n.mi. for ENU and radians longitude for LLA.
     * @param yMin the y-coordinate of the lower boundary of the terrain box.  This is in n.mi. for ENU and radians latitude for LLA.
     * @param yMax the y-coordinate of the upper boundary of the terrain box.  This is in n.mi. for ENU and radians latitude for LLA.
     * @param rowsFine the number of rows of terrain data on the fine grid (covering the vertical, y-direction of the terrain box).
     * @param colsFine the number of columns of terrain data on the fine grid (covering the horizontal, x-direction of the terrain box).
     * @param coarseRes the resolution to use to compute the coarse terrain grid.
     *          This is in n.mi. for ENU and radians for LLA (same in both x and y directions).
     */
    public void loadTerrain(short[] terrain_data, double xMin, double xMax,
        double yMin, double yMax, int rowsFine, int colsFine, double coarseRes)
    {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.yPointsFine = rowsFine;
        this.xPointsFine = colsFine;

        assert (terrain_data.length == rowsFine * colsFine) : "Input rows(" +
            rowsFine + ") and cols(" + colsFine +
            ") doesn't match actual terrain data size(" + terrain_data.length +
            ").";

        this.fineElevation = new short[terrain_data.length];
        for (int i = 0; i < terrain_data.length; i++)
        {
            fineElevation[i] = terrain_data[i];
        }

        //  Now compute the fine resolution
        this.xFineRes = (xMax - xMin) / colsFine;
        this.yFineRes = (yMax - yMin) / rowsFine;

        this.computeCoarseTerrain(coarseRes);
    }

    /**
     * Computes the coarse terrain at the specified resolution from the fine terrain.
     * The fine terrain must exist first before this method can be called.
     * @param coarseRes the resolution to use to compute the coarse terrain grid.
     *          This is in n.mi. for ENU and radians for LLA (same in both x and y directions).
     */
    private void computeCoarseTerrain(double coarseRes)
    {
        this.coarseRes = coarseRes;
        //  Now compute the number of coarse columns.
        this.xPointsCoarse = (int) Math.ceil((xMax - xMin) / coarseRes);

        //  local variable
        int yPointsCoarse = (int) Math.ceil((yMax - yMin) / coarseRes);

        this.coarseElevationMax = new short[xPointsCoarse * yPointsCoarse];
        this.coarseElevationMin = new short[xPointsCoarse * yPointsCoarse];

        //  Now need to figure this out.

        //  1) What if colsCoarse results in coarse xMax > fine xMax?  live with it, or try to average it out?  Or simply
        //     truncate the last col and row?  I think the latter.
        //  2) Looking for max and min in each coarse cell.
        for (int i = 0; i < xPointsCoarse; i++)
        {
            for (int j = 0; j < yPointsCoarse; j++)
            {
                double x1 = xMin + i * coarseRes;
                double x2 = Math.min(xMax, x1 + coarseRes);
                double y1 = yMin + j * coarseRes;
                double y2 = Math.min(yMax, y1 + coarseRes);

                double x = x1;
                short el_max = Short.MIN_VALUE;
                short el_min = Short.MAX_VALUE;

                while (x < x2)
                {
                    double y = y1;
                    while (y < y2)
                    {
                        int index = terrainCell(x, y);
                        if (index >= 0)
                        {
                            short el = fineElevation[index];

                            //
                            el_max = el > el_max ? el : el_max;
                            el_min = el < el_min ? el : el_min;
                        }

                        //
                        y += yFineRes;
                    }
                    x += xFineRes;
                }

                this.coarseElevationMax[j * xPointsCoarse + i] = el_max;
                this.coarseElevationMin[j * xPointsCoarse + i] = el_min;
            }
        }
    }

    public void readFiles(String directory)
    {
        //  First, discover all files in this location.
        FilenameFilter f = new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.contains(".dt");
            }
        };

        Collection<File> files = listFiles(new File(directory), f, true);

        ArrayList<TerrainFile> tfs = new ArrayList<TerrainFile>();

        this.xMin = Double.POSITIVE_INFINITY;
        this.xMax = Double.NEGATIVE_INFINITY;
        this.yMin = Double.POSITIVE_INFINITY;
        this.yMax = Double.NEGATIVE_INFINITY;

        for (File file : files)
        {
            TerrainFile tf = this.readFile(file);
            if (tf != null)
            {
                tfs.add(tf);
            }
        }

        //  1. Create the fine res array of appropriate dimension...this automatically inits it with zeros.
        this.xPointsFine = (int) Math.ceil((xMax - xMin) / xFineRes + 1.0);
        this.yPointsFine = (int) Math.ceil((yMax - yMin) / yFineRes + 1.0);
        this.fineElevation = new short[xPointsFine * yPointsFine];

        //  Sort terrain files -- this isn't necessary, but it helps with debugging so that I can better track what's what.
        Collections.sort(tfs, new TFComparator());

        //  2. For each terrain file, compute x, y coords for each point, lookup master terrain file index, and stuff in the value.
        for (TerrainFile tf : tfs)
        {
            for (int i = 0; i < tf.getXPointsFine(); i++)
            {
                double lat = tf.getXMin() + (double) i * tf.getXFineRes();

                for (int j = 0; j < tf.getYPointsFine(); j++)
                {
                    double lon = tf.getYMin() + (double) j * tf.getYFineRes();

                    this.fineElevation[this.terrainCell(lat, lon)] = tf.
                        getFineElevation()[tf.terrainCell(lat, lon)];
                }
            }
        }

        tfs.clear();
    }

    /**
     * Returns a collection of files within a directory.
     * @param directory Directory from which to read the files.
     * @param filter Filename filter
     * @param recurse true if search is recursive, false otherwise.
     * @return collection of files meeting the filter criteria.
     */
    public static Collection<File> listFiles(File directory,
        FilenameFilter filter, boolean recurse)
    {
        // List of files / directories
        ArrayList<File> files = new ArrayList<>();

        boolean isDir = directory.isDirectory();
        // Get files / directories in the directory
        File[] entries = directory.listFiles();

        // Go over entries
        for (File entry : entries)
        {
            // If there is no filter or the filter accepts the
            // file / directory, add it to the list
            if (filter == null || filter.accept(directory, entry.getName()))
            {
                files.add(entry);
            }

            // If the file is a directory and the recurse flag
            // is set, recurse into the directory
            if (recurse && entry.isDirectory())
            {
                files.addAll(listFiles(entry, filter, recurse));
            }
        }

        // Return collection of files
        return files;
    }

    /**
     * Reads in a DTED file
     * @param file file to read.
     * @return A terrain file object.
     */
    public TerrainFile readFile(File file)
    {
        TerrainFile tf = null;
        //  First, read the binary file into a memory stream of bytes.
        try
        {
            FileInputStream s = new FileInputStream(file);
            int n = s.available();
            byte[] bytes = new byte[n];

            s.read(bytes, 0, n);
            s.close();

            ByteArrayInputStream bstream = new ByteArrayInputStream(bytes);
            bstream.reset();

            tf = new TerrainFile();

            // consume a uhl record
            readBytes(bstream, 3 + 1 + 8 + 8 + 4 + 4 + 4 + 3 + 12 + 4 + 4 + 1 +
                24);

            // read DSI record and the first part of the WGS84 record
            readBytes(bstream, 3 + 1 + 2 + 27 + 26 + 5 + 15 + 8 + 2 + 1 + 4 + 4 +
                4 + 8 + 16 + 9 + 2 + 4 + 3 + 5 + 10 + 4 + 22);

            double xMinDeg = AngleUnit.DMS.convert(parseLat(bstream, 9),
                AngleUnit.DD);
            double yMinDeg = AngleUnit.DMS.convert(parseLon(bstream, 10),
                AngleUnit.DD);
            tf.setXMin(AngleUnit.DD.convert(xMinDeg, AngleUnit.RADIANS));
            tf.setYMin(AngleUnit.DD.convert(yMinDeg, AngleUnit.RADIANS));

            this.yMin = Math.min(this.yMin, tf.getYMin());
            this.xMin = Math.min(this.xMin, tf.getXMin());

            readBytes(bstream, 4 * 15 + 9);

            // Decimal Implied after the 3rd digit, hence the /10.0 - p14
            double lonDataIntervalInSeconds = parseDouble(bstream, 4) / 10.0;
            double latDataIntervalInSeconds = parseDouble(bstream, 4) / 10.0;

            tf.setYFineRes(AngleUnit.DD.convert(lonDataIntervalInSeconds /
                3600.0, AngleUnit.RADIANS));
            tf.setXFineRes(AngleUnit.DD.convert(latDataIntervalInSeconds /
                3600.0, AngleUnit.RADIANS));

            //  This assumes all files are of the same res -- they better be!
            this.xFineRes = tf.getXFineRes();
            this.yFineRes = tf.getYFineRes();

            tf.setXPointsFine(parseInt(bstream, 4));
            tf.setYPointsFine(parseInt(bstream, 4));
            tf.setFineElevation(new short[tf.getYPointsFine() * tf.
                getXPointsFine()]);
            int pctCover = parseInt(bstream, 2);

            tf.setYMax(AngleUnit.DD.convert(yMinDeg + (tf.getYPointsFine() - 1) *
                lonDataIntervalInSeconds / 3600., AngleUnit.RADIANS));
            tf.setXMax(AngleUnit.DD.convert(xMinDeg + (tf.getXPointsFine() - 1) *
                latDataIntervalInSeconds / 3600., AngleUnit.RADIANS));

            this.yMax = Math.max(this.yMax, tf.getYMax());
            this.xMax = Math.max(this.xMax, tf.getXMax());

            readBytes(bstream, 101 + 100 + 156);
            readBytes(bstream, 2700);
            //
            //            int[][] heights = new int[numLonLines][];
            //            for(int i = 0; i < numLonLines; i++)
            //            {
            //                heights[i] = new int[numLatLines];
            //            }

            for (int yIndex = 0; yIndex < tf.getYPointsFine(); yIndex++)
            {
                byte b = (byte) bstream.read();

                int blockNum = parseBinaryInt(bstream, 3);

                int lonCount = parseBinaryInt(bstream, 2);
                int latCount = parseBinaryInt(bstream, 2);

                double lon = tf.getYMin() + (double) lonCount * tf.getYFineRes();

                short lastElevation = -100;
                for (int xIndex = 0; xIndex < tf.getXPointsFine(); xIndex++)
                {
                    double lat = tf.getXMin() + (double) xIndex *
                        tf.getXFineRes();

                    short elevation = parseSignedShort(bstream);
                    if (elevation > 30000)
                    {
                        elevation = (short) (-1 *
                            (elevation & Short.MAX_VALUE));
                    }

                    if (elevation == 32767)
                    {
                        elevation = 0;
                    }

                    if (lastElevation == -100)
                    {
                        lastElevation = elevation;
                    }

                    if (Math.abs(elevation - lastElevation) > 10000)
                    {
                        elevation = lastElevation;
                    }

                    tf.getFineElevation()[tf.terrainCell(lat, lon)] = elevation;

                    lastElevation = elevation;
                }

                //Console.Write("CheckSum = ");
                bstream.read();
                bstream.read();
                bstream.read();
                bstream.read();
            }

            bstream.close();

        }
        catch (IOException e)
        {
        }

        return tf;
    }

    private static short parseSignedShort(ByteArrayInputStream stream)
    {
        short retVal = 0;
        short b1 = (short) stream.read();
        short b2 = (short) stream.read();

        short x = (short) (b1 & 0x80);
        if (x > 0)
        {
            short mask = (short) 0x7f;
            short b1Prime = (short) (b1 & mask);
            retVal = (short) (-1 * (short) ((short) (b1Prime << 8) + b2));
        }
        else
        {
            retVal = (short) ((short) (b1 << 8) + b2);
        }


        if (retVal < -20000)
        {
            //  throw new Exception();
        }
        return retVal;
    }

    private static int parseBinaryInt(ByteArrayInputStream stream, int num)
    {
        int x = 0;
        for (int i = 0; i < num; i++)
        {
            byte b = (byte) stream.read();

            x = (x << 8) + (int) b;
        }

        return (x);
    }

    /**
     * Reads the latitude from the stream and returns it in DMS
     * @param stream stream to read from
     * @param count number of bytes to read
     * @return latitude in DMS
     */
    private static double parseLat(ByteArrayInputStream stream, int count)
    {
        char[] buff = readBytes(stream, count);
        String latString = new String(buff);
        double val = parseDMS(latString);
        if (latString.charAt(count - 1) == 'S')
        {
            val *= -1;
        }

        return (val);
    }

    private static double parseLon(ByteArrayInputStream stream, int count)
    {
        char[] buff = readBytes(stream, count);
        String lonString = new String(buff);
        double val = parseDMS(lonString);
        if (lonString.charAt(count - 1) == 'W')
        {
            val *= -1;
        }
        return (val);
    }

    private static double parseDMS(String s)
    {
        int dot = s.lastIndexOf('.');
        int secLength = 4; // SS.S
        if (dot == -1)
        {
            dot = s.length() - 1;
            secLength = 2; // SS
        }
        int secStart = dot - 2;

        // 012345678    0123456789
        // DDMMSS.SH or DDDMMSS.SH or DDMMSSH
        double secs = Double.parseDouble(s.substring(secStart, secStart +
            secLength));
        double mins = Double.parseDouble(s.substring(secStart - 2, secStart));
        double degs = Double.parseDouble(s.substring(0, secStart - 2));

        double val = degs + (mins / 60.0) + (secs /
            3600.0);

        return (val);
    }

    private static double parseDouble(ByteArrayInputStream stream, int num)
    {
        return (Double.parseDouble(new String(readBytes(stream, num))));
    }

    private static int parseInt(ByteArrayInputStream stream, int num)
    {
        return (Integer.parseInt(new String(readBytes(stream, num))));
    }

    private static char[] readBytes(ByteArrayInputStream stream, int num)
    {
        char[] buff = new char[num];

        for (int i = 0; i <
            num; i++)
        {
            buff[i] = (char) stream.read();
        }

        return buff;
    }

    @Override
    public void fromXML(Node node)
    {
        //  Read in the loss tables and jammer pods.  Jammer pods are not systems, are they?
        NodeList children = node.getChildNodes();
        Double value = null;
        String units = null;
        String directory = null;
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);

            if (child.getNodeName().equalsIgnoreCase("coarse-resolution"))
            {
                Node f = child.getAttributes().getNamedItem("value");
                if (f != null)
                {
                    value = Double.parseDouble(f.getTextContent());
                }

                Node p = child.getAttributes().getNamedItem("units");
                if (p != null)
                {
                    units = p.getTextContent();
                }
            }
            else if (child.getNodeName().equalsIgnoreCase("directory"))
            {
                URL scenarioURL = null;
                try
                {
                    scenarioURL = new URL(ArgsHandler.getInstance().
                        getScenarioURL());
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }

                if (child.getTextContent().contains(":"))
                {
                    directory = child.getTextContent();
                }
                else
                {
                    directory = ArgsHandler.getInstance().getWorkingDirectory() +
                        java.io.File.separator + scenarioURL.getPath().substring(0, scenarioURL.getPath().
                        lastIndexOf("/") + 1) + child.getTextContent();
                }
            }
        }
        if (value != null && directory != null)
        {
            this.readFiles(directory);

            if (units != null)
            {
                value = AngleUnit.valueOf(units).convert(value,
                    AngleUnit.RADIANS);
//                if (units.equalsIgnoreCase("DD"))
//                {
//                    value = AngleUnit.DD.convert(value, AngleUnit.RADIANS);
//                }
//                else if (units.equalsIgnoreCase("DMS"))
//                {
//                    value = AngleUnit.DMS.convert(value, AngleUnit.RADIANS);
//                }
            }

            this.computeCoarseTerrain(value);
        }
    }

    @Override
    public void toXML(Node node)
    {
        //  TODO
    }

    /**
     * Contains all of the terrain data from a single terrain file.
     */
    public class TerrainFile
    {
        //
        //  Terrain data
        //
        //  High resolution elevation data (in meters)
        private short[] fineElevation;

        //  y-dimension (lat)
        private int yPointsFine;

        //  x-dimension (long)
        private int xPointsFine;

        private double xFineRes;

        private double yFineRes;

        //  The spatial domain covered by the terrain grid.  These must be in the
        //  same units as used for platform locations (i.e., n.mi. for "ENU" and radians for "LLA").
        private double xMin = Double.MAX_VALUE;

        private double xMax = Double.MIN_VALUE;

        private double yMin = Double.MAX_VALUE;

        private double yMax = Double.MIN_VALUE;

        public TerrainFile()
        {
            fineElevation = null;
        }

        public TerrainFile(TerrainFile src)
        {
            this.fineElevation = new short[src.fineElevation.length];
            for (int i = 0; i < this.fineElevation.length; i++)
            {
                this.fineElevation[i] = src.fineElevation[i];
            }

            this.xPointsFine = src.xPointsFine;
            this.yPointsFine = src.yPointsFine;
            this.xFineRes = src.xFineRes;
            this.yFineRes = src.yFineRes;
            this.xMax = src.xMax;
            this.yMax = src.yMax;
            this.xMin = src.xMin;
            this.yMin = src.yMin;
        }

        /**
         * @return the fineElevation
         */
        public short[] getFineElevation()
        {
            return fineElevation;
        }

        /**
         * @param fineElevation the fineElevation to set
         */
        public void setFineElevation(short[] fineElevation)
        {
            this.fineElevation = fineElevation;
        }

        /**
         * @return the rowsFine
         */
        public int getYPointsFine()
        {
            return yPointsFine;
        }

        /**
         *
         * @param yPointsFine
         */
        public void setYPointsFine(int yPointsFine)
        {
            this.yPointsFine = yPointsFine;
        }

        /**
         * @return the colsFine
         */
        public int getXPointsFine()
        {
            return xPointsFine;
        }

        /**
         *
         * @param xPointsFine
         */
        public void setXPointsFine(int xPointsFine)
        {
            this.xPointsFine = xPointsFine;
        }

        /**
         * @return the xFineRes
         */
        public double getXFineRes()
        {
            return xFineRes;
        }

        /**
         * @param xFineRes the xFineRes to set
         */
        public void setXFineRes(double xFineRes)
        {
            this.xFineRes = xFineRes;
        }

        /**
         * @return the yFineRes
         */
        public double getYFineRes()
        {
            return yFineRes;
        }

        /**
         * @param yFineRes the yFineRes to set
         */
        public void setYFineRes(double yFineRes)
        {
            this.yFineRes = yFineRes;
        }

        /**
         * @return the xMin
         */
        public double getXMin()
        {
            return xMin;
        }

        /**
         * @param xMin the xMin to set
         */
        public void setXMin(double xMin)
        {
            this.xMin = xMin;
        }

        /**
         * @return the xMax
         */
        public double getXMax()
        {
            return xMax;
        }

        /**
         * @param xMax the xMax to set
         */
        public void setXMax(double xMax)
        {
            this.xMax = xMax;
        }

        /**
         * @return the yMin
         */
        public double getYMin()
        {
            return yMin;
        }

        /**
         * @param yMin the yMin to set
         */
        public void setYMin(double yMin)
        {
            this.yMin = yMin;
        }

        /**
         * @return the yMax
         */
        public double getYMax()
        {
            return yMax;
        }

        /**
         * @param yMax the yMax to set
         */
        public void setYMax(double yMax)
        {
            this.yMax = yMax;
        }

        /**
         * Returns the index of the terrain cell covering the specified point.
         * This is useful for platforms to determine when they need to recalculate or invalidate LOS to other platforms.
         * @param  x x-coordinate in n.mi. (ENU) or latitude in radians (LLA)
         * @param  y y-coordinate in n.mi. (ENU) or longitude in radians (LLA)
         * @return terrain cell index (or -1 if not applicable or outside terrain bounds).
         */
        public int terrainCell(double x, double y)
        {
            int i = -1;
            if (this.fineElevation != null)
            {
                int yIndex = (int) ((y + 0.5 * this.yFineRes - this.yMin) /
                    this.yFineRes);
                int xIndex = (int) ((x + 0.5 * this.xFineRes - this.xMin) /
                    this.xFineRes);

                i = yIndex * this.xPointsFine + xIndex;

                if (i < 0 || i >= this.fineElevation.length)
                {
                    i = -1;
                }
            }

            return i;
        }
    }

    /**
     * A comparator for supporting sorting of the terrain files on location.
     */
    protected class TFComparator implements Comparator<TerrainFile>
    {
        @Override
        public int compare(TerrainFile o1, TerrainFile o2)
        {
            if (o1.getYMin() < o2.getYMin())
            {
                return -1;
            }
            else if (o1.getYMin() > o2.getYMin())
            {
                return 1;
            }
            else
            {
                //  Same Y
                if (o1.getXMin() < o2.getXMin())
                {
                    return -1;
                }
                else if (o1.getXMin() == o2.getXMin())
                {
                    //  Should never happen unless same file was read twice.
                    return 0;
                }
                else
                {
                    return 1;
                }
            }
        }
    }
}
