/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 * 
 */
package org.geowebcache.service.wms;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.Configuration;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";

    private static Log log = LogFactory.getLog(org.geowebcache.service.wms.WMSService.class);

    // Proxy requests that are not getmap ?
    private boolean proxyRequests = false;
    
    // Proxy requests that are not tiled=true?
    private boolean proxyNonTiledRequests = false;
    
    public WMSService() {
        super(SERVICE_WMS);
    }

    public Tile getTile(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException {
        String[] keys = { "layers", "request", "tiled" };
        String[] values = ServletUtils.selectedStringsFromMap(
                request.getParameterMap(), keys);

        // Look for requests that are not getmap
        String req = values[1];
        if (req != null && !req.equalsIgnoreCase("getmap")) {
            // One more chance
            if(values[0] == null || values[0].length() == 0) {    
                values[0] = ServletUtils.stringFromMap(request.getParameterMap(), "layer");
            }
            
            Tile tile = new Tile(values[0], request, response);
            tile.setHint(req.toLowerCase());
            tile.setRequestHandler(Tile.RequestHandler.SERVICE);
            return tile;
        }

        // Check whether this request is missing tiled=true
        if (proxyNonTiledRequests
                && (values[2] == null || !values[2].equalsIgnoreCase("true"))) {
            Tile tile = new Tile(values[0], request, response);
            tile.setHint(req);
            tile.setRequestHandler(Tile.RequestHandler.SERVICE);
            return tile;
        }

        // Look for layer
        String layers = values[0];
        if (layers == null) {
            throw new ServiceException(
                    "Unable to parse layers parameter from request.");
        }

        TileLayer tileLayer = Service.tlDispatcher.getTileLayer(layers);

        WMSParameters wmsParams = new WMSParameters(request);
        MimeType mimeType = null;
        String strFormat = wmsParams.getFormat();

        try {
            mimeType = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, "
                    + strFormat);
        }

        if (wmsParams.getSrs() == null) {
            throw new ServiceException("No SRS specified");
        }

        SRS srs = wmsParams.getSrs();
        //int srsIdx = tileLayer.getSRSIndex(srs);

        if (! tileLayer.supportsSRS(srs)) {
            throw new ServiceException("Unable to match requested SRS "
                    + wmsParams.getSrs() + " to those supported by layer");
        }

        BBOX bbox = wmsParams.getBBOX();
        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException(
                    "The bounding box parameter is missing or not sane");
        }

        int[] tileIndex = tileLayer.getGridLocForBounds(srs, bbox);
        
        // String strOrigin = wmsParams.getOrigin();
        // if (strOrigin != null) {
        // String[] split = strOrigin.split(",");
        // if (split.length != 2) {
        // throw new ServiceException("Unable to parse tilesOrigin,"
        // + "should not be set anyway: " + strOrigin);
        // }
        // double x = Double.valueOf(split[0]);
        // double y = Double.valueOf(split[1]);
        //
        // if (Math.abs(x + 180.0) < 0.5 && x + Math.abs(y + 90.0) < 0.5) {
        // // ok, fine for EPSG:4326
        // } else if (Math.abs(x + 20037508.34) < 1.0
        // && x + Math.abs(y + 20037508.34) < 1.0) {
        // // ok, fine for EPSG:9000913
        // } else {
        // throw new ServiceException("The tilesOrigin parameter "
        // + strOrigin
        // + " is not accepted by GeoWebCache, please omit"
        // + " or use lower left corner of world bounds.");
        // }
        // }

        return new Tile(layers, srs, tileIndex, mimeType, request, response);
    }

    public void handleRequest(TileLayerDispatcher tLD, Tile tile)
            throws GeoWebCacheException {

        if (tile.hint != null) {
            if(tile.hint.equalsIgnoreCase("getcapabilities")) {
                WMSRequests.handleGetCapabilities(tLD, tile.servletResp);
            } else {
                WMSRequests.handleProxy(tLD, tile);
            }
        } else {
            throw new GeoWebCacheException(
                    "The WMS Service would love to help, "
                  + "but has no idea what you're trying to do?"
                  + "Please include request URL if you file a bug report.");
        }
    }
    
    public void setConfig(List<Configuration> getCapConfigs) {
        WMSRequests.getCapConfigs = getCapConfigs;
        log.info("Got passed " + getCapConfigs.size() + " configuration objects for getCapabilities.");
    }
    
    public void setProxyRequests(String trueFalse) {
        this.proxyRequests = Boolean.parseBoolean(trueFalse);
        if(this.proxyRequests) {
            log.info("Will proxy requests to backend that are not getmap or getcapabilities.");
        } else {
            log.info("Will NOT proxy non-getMap requests to backend.");
        }
    }
    
    public void setProxyNonTiledRequests(String trueFalse) {
        this.proxyNonTiledRequests = Boolean.parseBoolean(trueFalse);
        if(this.proxyNonTiledRequests) {
            log.info("Will proxy requests that miss tiled=true to backend.");
        } else {
            log.info("Will NOT proxy requests that miss tiled=true to backend.");
        }
    }
}
