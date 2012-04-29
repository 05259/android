/* ownCloud Android client application
 *   Copyright (C) 2011  Bartek Przybylski
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package eu.alefzero.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.util.Log;

public class WebdavUtils {
  
  public static final String RESPONSE = "response";
  public static final String HREF = "href";
  public static final String IS_HIDDEN = "ishidden";
  public static final String RESOURCE_TYPE = "resourcetype";
  public static final String CONTENT_TYPE = "getcontenttype";
  public static final String CONTENT_LENGTH = "getcontentlength";
  public static final String LAST_MODIFIED = "getlastmodified";
  public static final String LAST_ACCESS = "lastaccessed";
  public static final String CREATE_DATE = "creationdate";
  
  public static final String PROPSTAT = "propstat";
  public static final String STATUS = "status";
  public static final String PROP = "prop";

  private static final String DAV_NAMESPACE_PREFIX = "DAV:";
  
  public static final SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh:mm");
  private static final SimpleDateFormat DATETIME_FORMATS[] = {
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
      new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'", Locale.US),
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
      new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US),
      new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
      new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)};
  
  public static String prepareXmlForPropFind() {
    String ret = "<?xml version=\"1.0\" ?><D:propfind xmlns:D=\"DAV:\"><D:allprop/></D:propfind>";
    return ret;
  }
  
  public static String prepareXmlForPatch() {
    return "<?xml version=\"1.0\" ?><D:propertyupdate xmlns:D=\"DAV:\"></D:propertyupdate>";
  }
  
  public static Date parseResponseDate(String date) {
    Date returnDate = null;
    for (int i = 0; i < DATETIME_FORMATS.length; ++i) {
      try {
        returnDate = DATETIME_FORMATS[i].parse(date);
        return returnDate;
      } catch (ParseException e) {}
    }
    return null;
  }
  
  private static String determineDAVPrefix(Element e) {
    for (int i = 0; i < e.getAttributes().getLength(); ++i) {
      String attrName = e.getAttributes().item(i).getNodeName();
      if (e.getAttribute(attrName).equals(DAV_NAMESPACE_PREFIX)) {
        return attrName.substring(attrName.lastIndexOf(':')+1) + ":";
      }
    }
    return null;
  }
}
