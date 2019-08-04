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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.ridderware.fuse.Double3D;

/**
 * Utility class to provide rapid line-of-site services to platforms.  LOSUtil remembers the
 * last grid cell which each platform was in for its last LOS check and will only recompute LOS
 * whenever either of the two platforms of interest has moved into a different terrain grid cell.
 * When a platform does move into a new terrain cell, we invalidate all LOS entries to that platform,
 * forcing any LOS check involving that platform to recompute on next request.  The los data is stored in
 * a lower triangular matrix to reduce storage and duplicate reference to the same los between two platforms.
 * e.g., id = 0 refs ID's {1...N-1}
 * id = 1 refs ID's {2...N-1}
 * .
 * .
 * .
 * id = N-2 refs ID {N-1}
 *
 * @author Jeff Ridder
 */
public class LOSUtil
{
    private CMWorld world;

    //  Map of platform ID to LOS matrix ID.  This is filled out as platforms call this
    //  class to get LOS services.
    private Map<Integer, Integer> id_id_map;

    //  Map of ID's to the their current terrain cell
    private Map<Integer, Integer> id_cell_map;

    //  Now, how to store the LOS matrix for quickest access to LOS data and invalidation?
    private Map<Integer, int[]> los_map;

    private int last_id;

    private IEarthModel.EarthFactor earth_factor;

    /**
     * Creates a new instance of LOSUtil.
     * @param world the world serviced by this utility.
     * @param earth_factor Earth radius factor for LOS calculations.
     */
    public LOSUtil(CMWorld world, IEarthModel.EarthFactor earth_factor)
    {
        this.world = world;
        id_id_map = new HashMap<>();
        id_cell_map = new HashMap<>();
        los_map = new HashMap<>();
        last_id = 0;
        this.earth_factor = earth_factor;
    }

    /**
     * Called by the world to reset the LOS maps at the beginning of each run.
     */
    public void reset()
    {
        last_id = 0;
        id_id_map.clear();
        id_cell_map.clear();
        los_map.clear();

        Set<Platform> platforms = world.getPlatforms();
        //  Fill the id_id_map and create the los whiteboard
        for (Platform p : platforms)
        {
            id_id_map.put(p.getId(), this.getID(p));
        }

        assert (platforms.size() == last_id);

        //  Create the LOS whiteboard as a lower triangular matrix.
        //  e.g., id = 0 refs ID's {1...N-1}
        //  id = 1 refs ID's {2...N-1}
        //  .
        //  .
        //  id = N-2 refs ID {N-1}

        for (int i = 0; i <= last_id - 2; i++)
        {
            int[] a = new int[last_id - 1 - i];
            for (int j = 0; j < a.length; j++)
            {
                a[j] = -1;  //  marked as invalidated LOS
            }
            los_map.put(i, a);
        }
    }

    /**
     * Computes, returns, and stores the line-of-site between two platforms.
     * @param p1 platform 1
     * @param p2 platform 2
     * @return true if line-of-site exists, false otherwise.
     */
    public boolean hasLOS(Platform p1, Platform p2)
    {
        boolean los = false;

        if (CMWorld.getTerrainModel() instanceof BaldEarthTerrain)
        {
            //  This is a hack because it is specific to a particular terrain model.  But we do this
            //  for the sake of efficiency when there is no terrain.  There will always be only one
            //  model like Bald earth (RIGHT???), so testing for this in order to accelerate should be okay
            //  even if it looks and feels dirty.
            los = CMWorld.getTerrainModel().hasLOS(p1.getLocation(),
                p2.getLocation(), earth_factor);
        }
        else
        {
            //  The general case where terrain data exists.  This section works for bald earth as well, but isn't
            //  as fast.
            int id1 = getID(p1);
            int id2 = getID(p2);

            Double3D loc1 = p1.getLocation();
            Integer cell1 =
                CMWorld.getTerrainModel().terrainCell(loc1.getX(),
                loc1.getY());

            if (!cell1.equals(this.id_cell_map.get(id1)) || cell1 < 0)
            {
                invalidate(id1);
                id_cell_map.put(id1, cell1);
            }

            Double3D loc2 = p2.getLocation();
            Integer cell2 =
                CMWorld.getTerrainModel().terrainCell(loc2.getX(),
                loc2.getY());

            if (!cell2.equals(this.id_cell_map.get(id2)) || cell2 < 0)
            {
                invalidate(id2);
                id_cell_map.put(id2, cell2);
            }



            int id = id1 < id2 ? id1 : id2;
            int[] a = los_map.get(id);
            int j = Math.max(id1, id2) - id - 1;

            if (a[j] < 0)
            {
                //  We found an invalidated los_map entry...must re-compute

                //  Compute and store new LOS data
                los = CMWorld.getTerrainModel().hasLOS(loc1, loc2,
                    earth_factor);

                if (los)
                {
                    a[j] = 1;
                }
                else
                {
                    a[j] = 0;
                }
            }
            else
            {
                //  retrieve the LOS from the stored data
                if (a[j] == 1)
                {
                    los = true;
                }
                else if (a[j] == 0)
                {
                    los = false;
                }
                else
                {
                    assert (false) : "unreachable condition reached!";
                }
            }
        }

        return los;
    }

    private int getID(Platform p)
    {
        Integer id = id_id_map.get(p.getId());
        if (id == null)
        {
            id = last_id++;
            id_id_map.put(p.getId(), id);
        }
        return id;
    }

    private void invalidate(int id)
    {
        //
        //  Invalidate los data for the specified ID resets all LOS results concerning that ID
        //

        //  First, invalidate the map entry itself.
        int[] a = los_map.get(id);


        if (a != null)
        {
            for (int j = 0; j < a.length; j++)
            {
                a[j] = -1;
            }
        }

        //  Now invalidate all reference to id in lower map entries
        for (int i = 0; i < id; i++)
        {
            a = los_map.get(i);

            int j = id - i - 1;
            a[j] = -1;
        }
    }
}
