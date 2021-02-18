/*
 * (c) 2021 Jiri Gabriel <tykefcz@gmail.com>
 * This code is licensed under MIT license (see license.txt for details)
 */

package com.google.tykefcz.artools;


import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.Editor;
import processing.app.PreferencesData;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;
import processing.app.helpers.PreferencesMap;
import static processing.app.I18n.tr;

public class BoardsUtil {
  public static String boardFqbn(TargetBoard b) {
    if (b == null) return "";
    try { 
      TargetPlatform tpl = b.getContainerPlatform();
      return tpl.getContainerPackage().getId() 
           + ":" + tpl.getId() 
           + ":" + b.getId();
    } catch (Exception e) { }
    return "";
  }
  
  protected static JMenuItem findBoardInMenu(Object menu, String fqbn) {
    if (menu == null) return null;
    JMenuItem rv = null;
    if (menu instanceof JMenu) {
      JMenu m = (JMenu) menu;
      for (int i = 0; i < m.getItemCount(); i++) {
        if ((rv = findBoardInMenu(m.getItem(i),fqbn)) != null)
          return rv;
      }
    } else if (menu instanceof javax.swing.JRadioButtonMenuItem) {
      JMenuItem menuItem = (JMenuItem)menu;
      try {
        if (boardFqbn((TargetBoard) menuItem.getAction().getValue("b")).equals(fqbn))
          return menuItem;
      } catch (Exception ie) { };
    }
    return null;
  }

  public static List<String> installedFqbs() {
    TargetBoard tbo = null;
    ArrayList<String> rv = new ArrayList<String>(20);
    try {
      for (TargetPackage tp : BaseNoGui.packages.values()) {
        for (TargetPlatform ta : tp.getPlatforms().values()) {
          String id = ta.getContainerPackage().getId() + ":"
                    + ta.getId() + ":";
          for (TargetBoard b : ta.getBoards().values()) {
            rv.add(id + ":" + b.getId());
          }
        }
      }
    } catch (Exception e) {}
    return rv;
  }  

  public static TargetBoard getBoard(String cfg) {
    TargetBoard tbo = null,maybe = null;
    try {
      if (cfg == null) return null;
      String[] ca = cfg.split(":",4);
      if (ca.length == 0 || ca.length == 2)
        return null;
      else if (ca.length == 1)
        ca = new String[] {"","",cfg}; // without package/platform
      String fqbn = ca[0] + ":" + ca[1] + ":" + ca[2];
      for (TargetPackage tp : BaseNoGui.packages.values()) {
        for (TargetPlatform ta : tp.getPlatforms().values()) {
          TargetBoard b = ta.getBoard(ca[2]);
          if (b != null && ca[2].equals(b.getId())) {
            if (boardFqbn(b).equals(fqbn))
              tbo = b;
            else
              maybe = b;
          }
          if (tbo != null) break;
        }
        if (tbo != null) break;
      }
      if (tbo == null && maybe != null)
        tbo = maybe;
      maybe = null;
    } catch (Exception e) { }
    return tbo;
  }
  
  @SuppressWarnings("unchecked")
  public static JMenuItem getMenuForBoard(Base base, TargetBoard tbo) {
    JMenuItem toActivate = null;
    try {
      if (tbo == null || base == null) return null;
      String fqbn = boardFqbn(tbo);
      toActivate = findBoardInMenu(base.getBoardsCustomMenus().get(0),fqbn);
    } catch (Exception ex) {
      ex.printStackTrace();
    };
    return toActivate;
  }

  // return: 0 - OK, 1 - warning, 2 - error
  public static int activateBoard(String boardFqbn) {
    return activateBoard(boardFqbn,false);
  }

  // return: 0 - OK, 1 - warning, 2 - error
  @SuppressWarnings("unchecked")
  public static int activateBoard(String boardFqbn, boolean verbose) {
    TargetBoard tbo = getBoard(boardFqbn);
    Base base = Base.INSTANCE;
    int warncount = 0;
    if (tbo == null || base == null) return 2;
    JMenuItem toActivate = BoardsUtil.getMenuForBoard(base,tbo);
    try {
      if (toActivate != null && base != null) {
        //System.out.println("set " + boardFqbn + " M=" + toActivate.getText());
        String[] cfs = boardFqbn.split(":",4);
        if (cfs.length == 4) {
          String bid = cfs[2] + "_"; // board id
          for (String nvpair : cfs[3].split(",")) {
            String[] nv = nvpair.split("=",2);
            if (nv.length == 2 && !(nv[0].equals("") || nv[1].equals(""))) {
              if (nv[0].equals("CONSOLEBAUD")) 
                PreferencesData.set("serial.debug_rate",nv[1]);
              else
                PreferencesData.set("custom_" + nv[0],bid + nv[1]);
            }
          }
        }
        // PreferencesData.set("board",cfs[2]);
        // PreferencesData.set("target_package",cfs[0]);
        // PreferencesData.set("target_platform",cfs[1]);
        toActivate.setSelected(true);
        java.awt.event.ActionEvent ev = new java.awt.event.ActionEvent(toActivate,
                        java.awt.event.ActionEvent.ACTION_PERFORMED, "");
        for (java.awt.event.ActionListener listener : toActivate.getActionListeners())
          listener.actionPerformed(ev);
        if (verbose)
          System.out.println("Activated " + toActivate.getText() + ":" + boardFqbn);
        TargetPlatform platform = tbo.getContainerPlatform();
        PreferencesMap customMenus = platform.getCustomMenus();
        String platUID = platform.getId() + "_" + platform.getFolder();
        List<JMenu> boardsCustomMenus = base.getBoardsCustomMenus();
        if (cfs.length == 4 
            && boardsCustomMenus != null && boardsCustomMenus.size() > 0) {
          String bid = cfs[2] + "_"; // board id
          HashMap<String,String> cfsmap = new HashMap<String,String>();
          for (String nvpair : cfs[3].split(",")) {
            String[] nv = nvpair.split("=",2);
            if (nv.length == 2 
                && !(nv[0].equals("") || nv[1].equals("") || nv[0].equals("CONSOLEBAUD"))) {
              if (tbo.hasMenu(nv[0])) {
                cfsmap.put(tr(customMenus.get(nv[0])),nv[1]);
                //System.out.println(nv[0] + " -> " + tr(customMenus.get(nv[0])) + " <= " + nv[1]);
              } else {
                warncount++;
                if (verbose)
                  System.out.println("Option '" + nv[0] + "'='" + nv[1] + "' not found in menu");
              }
            }
          }
          for (int i = 1; i < boardsCustomMenus.size();i++) {
            JMenu jm = boardsCustomMenus.get(i);
            String[] jmtxt = jm.getText().split(":",2);
            if (   jm.isVisible() 
                && platUID.equals(jm.getClientProperty("platform"))
                && cfsmap.containsKey(jmtxt[0])) {
              JRadioButtonMenuItem rbmi = null;
              String opt=cfsmap.get(jmtxt[0]);
              for (int n = 0; n < jm.getItemCount() && rbmi == null; n++) {
                JMenuItem jmi = jm.getItem(n);
                if (jmi instanceof JRadioButtonMenuItem && jmi.isVisible())
                try {
                  Action a = jmi.getAction();
                  String x = (String)a.getValue("custom_menu_option");
                  if (x != null && x.equals(opt)) {
                    rbmi = (JRadioButtonMenuItem)jmi;
                    jmi.setSelected(true);
                    a.actionPerformed(ev);
                    //System.out.println("Actived " + jmtxt[0] + "=" + opt);
                    cfsmap.remove(jmtxt[0]);
                    break;
                  }
                } catch (Exception ex) { ex.printStackTrace();}
              } // for n (Items)
            } // valid option for platform
            if (cfsmap.size() == 0) break; // all done
          } // walk through boardCustomMenus
          if (cfsmap.size() > 0) {
            for (String m : cfsmap.keySet()) {
              warncount++;
              if (verbose)
                System.out.println("Option " + m + "=" + cfsmap.get(m)  + " menu not found!");
            }
          }
        }
        return (warncount > 0?1:0);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return 2;
  } // activateBoard
  
  public static String prefsForBoard() {
    PreferencesMap allp = PreferencesData.getMap();
    TargetBoard tb = BaseNoGui.getTargetBoard();
    TargetPlatform tp = tb.getContainerPlatform();

    PreferencesMap pdm = tp.getCustomMenus();
    StringBuilder sb = new StringBuilder();
    String bid = tb.getId();
    // package:platform:board[:arg=val[,arg2=val2]...]
    sb.append(tp.getContainerPackage().getId()
            + ":" + tp.getId() 
            + ":" + bid);
    String delim = ":";
    bid += "_";
    for (String key : pdm.keySet()) {
      if (tb.hasMenu(key) && allp.containsKey("custom_" +  key)) {
        String v = allp.get("custom_" +  key);
        if (v.startsWith(bid)) {
          sb.append(delim + key + "=" + v.substring(bid.length()));
          delim = ",";
        }
      }
    }
    sb.append(delim + "CONSOLEBAUD=");
    if (allp.containsKey("serial.debug_rate"))
      sb.append(allp.get("serial.debug_rate"));
    else
      sb.append("19200");
    
    return sb.toString();
  }
}
