/*
 *  This file is part of the Wayback archival access software
 *   (http://archive-access.sourceforge.net/projects/wayback/).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.wayback.accesscontrol.robotstxt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.archive.wayback.util.ByteOp;

/**
 * Class which parses a robots.txt file, storing the rules contained therein,
 * and then allows for testing if path/userAgent tuples are blocked by those
 * rules.
 *
 * @author brad
 * @version $Date$, $Revision$
 */
public class RobotRules {
	
	private static final long serialVersionUID = 2917420727021840982L;
	private static final Logger LOGGER = Logger.getLogger(RobotRules.class
			.getName());
	/**
	 * Special name for User-agent which matches all values
	 */
	public static final String GLOBAL_USER_AGENT = "*";
	
	protected static final Pattern USER_AGENT_PATTERN = Pattern.compile("(?i)^User-agent:.*");
	protected static final Pattern DISALLOW_PATTERN = Pattern.compile("(?i)Disallow:.*");
	protected static final Pattern ALLOW_PATTERN = Pattern.compile("(?i)Allow:.*");
	
	private boolean bSyntaxErrors = false;
	private HashMap<String, ArrayList<String>> rules = 
		new HashMap<String, ArrayList<String>>();

	private LinkedList<String> userAgents = new LinkedList<String>();

	/**
	 * @return true if the robots.txt file looked suspicious, currently meaning
	 * we found a Disallow rule that was not preceded by a "User-agent:" line
	 */
	public boolean hasSyntaxErrors() {
		return bSyntaxErrors;
	}
	
	/**
	 * @return a List of all UserAgents Found in the Robots.txt document
	 */
	public List<String> getUserAgentsFound() {
		return userAgents;
	}
	
	/**
	 * Read rules from InputStream argument into this RobotRules, as a 
	 * side-effect, sets the bSyntaxErrors property.
	 * 
	 * @param is InputStream containing the robots.txt document
	 * @throws IOException for usual reasons
	 */
	public void parse(InputStream is) throws IOException {
		
		BufferedReader br = new BufferedReader(new InputStreamReader(
				(InputStream) is,ByteOp.UTF8));
        String read;
        boolean allowRuleFound = false;
        ArrayList<String> current = null;
        while (br != null) {
            do {
                read = br.readLine();
                // Skip comments & blanks
            } while ((read != null) && ((read = read.trim()).startsWith("#") ||
                read.length() == 0));
            if (read == null) {
            	br.close();
            	br = null;
            } else {
                int commentIndex = read.indexOf("#");
                if (commentIndex > -1) {
                    // Strip trailing comment
                    read = read.substring(0, commentIndex);
                }
                read = read.trim();
                if (USER_AGENT_PATTERN.matcher(read).matches()) {
                    String ua = read.substring(11).trim().toLowerCase();
                    if (current == null || current.size() != 0 || allowRuleFound) {
                        // only create new rules-list if necessary
                        // otherwise share with previous user-agent
                        current = new ArrayList<String>();
                    }
                    rules.put(ua, current);
                    allowRuleFound = false;
                    LOGGER.fine("Found User-agent(" + ua + ") rules...");
                    continue;
                } else if (DISALLOW_PATTERN.matcher(read).matches()) {
                	if (current == null) {
                        // buggy robots.txt
                    	bSyntaxErrors = true;
                        continue;
                    }
                    String path = read.substring(9).trim();
                    current.add(path);
                    continue;
                } else if (ALLOW_PATTERN.matcher(read).matches()) {
                	// Mark that there was an allow rule to clear the current list for next user-agent
                	allowRuleFound = true;
                }
                // unknown line; do nothing for now
                
                // TODO: check for "Allow" lines, and flag a syntax error if 
                //       we encounter any unknown lines?
            }
        }
    }
	
	private boolean blocksPath(String path, String curUA, List<String> uaRules) {
		
		Iterator<String> disItr = uaRules.iterator();
		while (disItr.hasNext()) {
			String disallowedPath = disItr.next();
			if (disallowedPath.length() == 0) {

				if (LOGGER.isLoggable(Level.INFO)) {
					LOGGER.info("UA(" + curUA
							+ ") has empty disallow: Go for it!");
				}
				return false;

			} else {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine("UA(" + curUA + ") has (" + disallowedPath
							+ ") blocked...(" + disallowedPath.length() + ")");
				}
				if (disallowedPath.equals("/") || path.startsWith(disallowedPath)) {
					if (LOGGER.isLoggable(Level.INFO)) {
						LOGGER.info("Rule(" + disallowedPath + ") applies to (" +
								path + ")");
					}
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Checks first the specified ua UserAgent, if rules are present for it,
	 * and then falls back to using rules for the '*' UserAgent.
	 * 
	 * @param path String server relative path to check for access
	 * @param ua String user agent to check for access
	 * @return boolean value where true indicates the path is blocked for ua
	 */
	public boolean blocksPathForUA(String path, String ua) {

		if(rules.containsKey(ua.toLowerCase())) {

			return blocksPath(path,ua,rules.get(ua.toLowerCase()));

		} else if(rules.containsKey(GLOBAL_USER_AGENT)) {

			return blocksPath(path,GLOBAL_USER_AGENT,
					rules.get(GLOBAL_USER_AGENT));			
		}
		return false;
	}
}
