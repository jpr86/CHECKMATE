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

import com.ridderware.fuse.Double3D;
import com.ridderware.fuse.MutableDouble3D;

/**
 * Round Earth model.  
 *
 * @author Jeff Ridder
 */
public class RoundEarth implements IEarthModel
{
    /** Earth's radius in nautical miles. */
    public static final double EARTH_RADIUS_NMI = 3443.887;

    //  Arc angle corresponding to the two points.  Prevents recalculation unless necessary.
    private double arc_angle;

    private double true_dist = 0;

    private MutableDouble3D arc_pt1;

    private MutableDouble3D arc_pt2;

    /** Creates a new instance of RoundEarth */
    public RoundEarth()
    {
//        this.returnable_location = new MutableDouble3D();
        this.arc_angle = -1;
        this.true_dist = -1;
        this.arc_pt1 = new MutableDouble3D();
        this.arc_pt2 = new MutableDouble3D();
    }

    /**
     * Returns the coordinate system as a String.
     *
     * @return coordinate system in use.
     */
    @Override
    public final String getCoordinateSystem()
    {
        return "LLA";
    }

    /**
     *  Computes the line-of-site distance between two points.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      line of site distance in nmi.
     */
    @Override
    public final double trueDistance(Double3D pt1, Double3D pt2)
    {
        double distance;
        if (this.arc_pt1.equals(pt1) && this.arc_pt2.equals(pt2) &&
            this.true_dist >= 0.)
        {
            distance = this.true_dist;
        }
        else
        {
            final double arc = gcAlpha(pt1, pt2);
            final double h1 = pt1.getZ() + EARTH_RADIUS_NMI;
            final double h2 = pt2.getZ() + EARTH_RADIUS_NMI;

            distance = Math.sqrt(h1 * h1 + h2 * h2 - 2. * h1 * h2 *
                Math.cos(arc));
            this.true_dist = distance;
        }
        return distance;
    }

    /**
     * Returns the square of the true line-of-site distance between two points.
     * Although it might appear redundant, we include it in this interface since many calculations can
     * be significantly accelerated by computing and working with the square of the distance.
     *
     * @param  pt1  Double3D object of point 1.
     * @param  pt2  Double3D object of point 2.
     * @return      square of the line of site distance in nmi-squared.
     */
    @Override
    public final double trueDistanceSq(Double3D pt1, Double3D pt2)
    {
        double distSq;
        if (this.arc_pt1.equals(pt1) && this.arc_pt2.equals(pt2) &&
            this.true_dist >= 0.)
        {
            distSq = this.true_dist * this.true_dist;
        }
        else
        {
            final double arc = gcAlpha(pt1, pt2);
            final double h1 = pt1.getZ() + EARTH_RADIUS_NMI;
            final double h2 = pt2.getZ() + EARTH_RADIUS_NMI;

            distSq = h1 * h1 + h2 * h2 - 2. * h1 * h2 * Math.cos(arc);
        }
        return distSq;
    }

    /**
     *  Projects the ending location given a staring point, distance, and azimuth.
     *
     * @param  pt     starting point.
     * @param  distance  distance in nmi.
     * @param  azimuth   azimuth between +/- PI and in radians.
     * @return           ending location.
     */
    @Override
    public final Double3D projectLocation(Double3D pt, double distance,
        double azimuth)
    {
        final double h = EARTH_RADIUS_NMI + pt.getZ();
        final double ch = h * Math.PI;

        final double newlat = Math.asin(Math.max(Math.min(Math.cos(Math.abs(
            distance * Math.PI / ch + pt.getX() - Math.PI / 2.)) - (1. -
            Math.cos(azimuth)) * Math.cos(pt.getX()) *
            Math.sin(distance * Math.PI / ch), 1.), -1.));

        //	have to break this into multiple terms or else it introduces a small
        //	(but annoying) error.
        final double term1 = (Math.cos(distance * Math.PI / ch) -
            Math.sin(pt.getX()) * Math.sin(newlat));

        final double term2 = Math.cos(pt.getX()) * Math.cos(newlat);

        final double term3 = Math.max(Math.min(term1 / term2, 1.), -1.);

        final double delta_long = Math.abs(Math.acos(term3));

        double newlong = pt.getY();

        if (azimuth < 0.)
        {
            newlong -= delta_long;
        }
        else
        {
            newlong += delta_long;
        }

        return new Double3D(newlat, newlong, h - EARTH_RADIUS_NMI);
    }

    /**
     *  Interpolates between two points to find an intermediate point.  If the input distance is greater
     *  than the distance between the two points, the ending point is returned.
     *
     * @param  pt1  starting point in LLA.
     * @param  pt2  ending point in LLA.
     * @param  distance distance from starting point in nmi.
     * @return  intermediate point.
     */
    @Override
    public final Double3D interpolateLocation(Double3D pt1, Double3D pt2,
        double distance)
    {
        //	From distance calcs worksheet
        final double angular_dist = gcAlpha(pt1, pt2);
        double td = this.true_dist;
        if (td < 0.)
        {
            td = trueDistance(pt1, pt2);
        }

//        double fraction = Math.min(1., distance / (angular_dist * 180. * 60. / Math.PI));
        //  This fraction is the fraction of the 3D distance, while the one commented out above is the fraction
        //  of only the XY surface distance.
        final double fraction = Math.min(1., distance / td);

        double new_lat = pt1.getX();
        double new_long = pt1.getY();

        if (angular_dist > 0.)
        {
            final double a = Math.sin((1. - fraction) * angular_dist) /
                Math.sin(angular_dist);
            final double b = Math.sin(fraction * angular_dist) /
                Math.sin(angular_dist);
            final double x = a * Math.cos(pt1.getX()) * Math.cos(pt1.getY()) +
                b * Math.cos(pt2.getX()) * Math.cos(pt2.getY());
            final double y = a * Math.cos(pt1.getX()) * Math.sin(pt1.getY()) +
                b * Math.cos(pt2.getX()) * Math.sin(pt2.getY());
            final double z = a * Math.sin(pt1.getX()) + b *
                Math.sin(pt2.getX());

            new_lat = Math.atan2(z, Math.sqrt(x * x + y * y));
            new_long = Math.atan2(y, x);
        }

        return new Double3D(new_lat, new_long, pt1.getZ() + fraction *
            (pt2.getZ() - pt1.getZ()));
    }

    /**
     *  Calculates the azimuth angle from point 1 to point 2.
     *
     * @param  pt1  point 1.
     * @param  pt2  point 2.
     * @return      azimuth angle between two points between +/- PI in radians.
     */
    @Override
    public final double azimuthAngle(Double3D pt1, Double3D pt2)
    {
        return AngleUnit.normalizeAngle(gcBearing(pt1, pt2), -Math.PI);
    }

    /**
     *  Calculates the elevation angle from point 1 to point 2.
     *
     * @param  pt1     point 1
     * @param  pt2     point 2
     * @param  k       Earth factor
     * @return         elevation angle in radians.
     */
    @Override
    public final double elevationAngle(Double3D pt1, Double3D pt2,
        EarthFactor k)
    {
        final double distance = trueDistance(pt1, pt2);

        final double rh1 = EARTH_RADIUS_NMI * k.value() + pt1.getZ();
        final double rh2 = EARTH_RADIUS_NMI * k.value() + pt2.getZ();

        return Math.acos((rh1 * rh1 + distance * distance - rh2 * rh2) / 2. /
            rh1 / distance) - Math.PI / 2.;
    }

    /**
     *  Computes the great circle arc angle between two points for ROUND earth model.
     *
     * @param  pt1  LLA coordinates of pt1
     * @param  pt2  LLA coordinates of pt2
     * @return      angle in radians.
     */
    public final double gcAlpha(Double3D pt1, Double3D pt2)
    {
        double arc = -1.;

        if (this.arc_pt1.equals(pt1) && this.arc_pt2.equals(pt2))
        {
            arc = this.arc_angle;
        }
        else if (pt1 != null && pt2 != null)
        {
            final double x1 = pt1.getX();
            final double x2 = pt2.getX();
            //  We're going to change this around using a trig identity to reduce
            //  the number of trig calls.
            //  Current calc (6 trig calls):  arc = acos[sin(x1)*sin(x2)+cos(x1)*cos(x2)*cos(y2-y1)]
            //  Trig identity 1:  sin(x1)*sin(x2) = 1/2[cos(x1-x2)-cos(x1+x2)]
            //  Trig identity 2:  cos(x1)*cos(x2) = 1/2[cos(x1-x2)+cox(x1+x2)]
            //  Substituting these into current calc gives:
            //  New calc (4 trig calls):  arc = acos[1/2((1+cos(y2-y1))*cos(x1-x2)+(cos(y2-y1)-1)cos(x1+x2))]

//            arc = Math.acos(Math.sin(pt1.getX()) * Math.sin(pt2.getX()) +
//                    Math.cos(pt1.getX()) * Math.cos(pt2.getX()) * Math.cos(pt2.getY()
//                    - pt1.getY()));

            final double cosy21 = Math.cos(pt2.getY() - pt1.getY());
            arc = Math.acos(0.5 * ((1 + cosy21) * Math.cos(x1 - x2) + (cosy21 -
                1) * Math.cos(x1 + x2)));

            this.arc_angle = arc;
            this.true_dist = -1;
            this.arc_pt1.setXYZ(pt1);
            this.arc_pt2.setXYZ(pt2);
        }
        return arc;
    }

    /**
     *  Computes the great circle distance given the arc angle for ROUND earth model.
     *
     * @param  gc_alpha  : arc angle (result of gcAlpha).
     * @return           distance in nmi.
     */
    private final double gcDistance(double gc_alpha)
    {
        return (gc_alpha * 180. * 60. / Math.PI);
    }

    /**
     *  Computes the great circle distance between two points for the ROUND earth model.
     *
     * @param  pt1  LLA coordinates of pt1
     * @param  pt2  LLA coordinates of pt2
     * @return      distance in nmi.
     */
    private final double gcDistance(Double3D pt1, Double3D pt2)
    {
        double dist = -1.;
        if (pt1 != null && pt2 != null)
        {
            dist = gcDistance(gcAlpha(pt1, pt2));
        }
        return dist;
    }

    /**
     *  Computes the great circle bearing from one point to another. This version
     *  is faster than the one without alpha as a parameter since that one must first
     *  compute gc_alpha.  This is for the ROUND earth model.
     *
     * @param  pt1       LLA coordinates of pt1
     * @param  pt2       LLA coordinates of pt2
     * @param  gc_alpha  result of gcAlpha
     * @return           bearing in radians relative to north (east is positive).
     */
    private final double gcBearing(Double3D pt1, Double3D pt2, double gc_alpha)
    {
        double bearing = -1.;
        if (pt1 != null && pt2 != null)
        {

            final double temp = Math.acos(Math.max(Math.min(1. - ((Math.cos(Math.abs(
                gc_alpha + pt1.getX() - Math.PI / 2.)) - Math.sin(pt2.getX())) /
                Math.cos(pt1.getX()) / Math.sin(gc_alpha)), 1.), -1.));

            if (pt2.getY() < pt1.getY())
            {
                bearing = 2. * Math.PI - temp;
            }
            else
            {
                bearing = temp;
            }
        }

        return bearing;
    }

    /**
     *  Computes the great circle bearing from one point to another.  This is for the ROUND
     *  earth model.
     *
     * @param  pt1  LLA coordinates of pt1
     * @param  pt2  LLA coordinates of pt2
     * @return      bearing in radians relative to north (east is positive)
     */
    private final double gcBearing(Double3D pt1, Double3D pt2)
    {
        double bearing = -1.;
        if (pt1 != null && pt2 != null)
        {
            bearing = gcBearing(pt1, pt2, gcAlpha(pt1, pt2));
        }
        return bearing;
    }
}
