/*
 * (c) 2021 Jiri Gabriel <tykefcz@gmail.com>
 * This code is licensed under MIT license (see license.txt for details)
 */

package com.google.tykefcz.iniino;

import java.awt.Dialog.ModalityType;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.Editor;
import processing.app.EditorTab;
import processing.app.PreferencesData;
import processing.app.Sketch;
import processing.app.Theme;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;
import processing.app.helpers.PreferencesMap;
import processing.app.syntax.SketchTextArea;
import processing.app.tools.Tool;

import static processing.app.I18n.tr;

/**
 * @author Jiri Gabriel
 */
public class IniIno implements Tool {

  public static Editor editor;
  public static long startTimestamp = 0;
  
  public static IIPanel panel = null;
  public static JDialog dialog = null;
  private static Runnable afterSkechLoad = null;

  protected static String boardFqbn(TargetBoard b) {
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
  
  protected static TargetBoard getBoard(String cfg) {
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
  protected static JMenuItem getMenuForBoard(TargetBoard tbo) {
    JMenuItem toActivate = null;
    try {
      if (tbo == null) return null;
      String fqbn = boardFqbn(tbo);
      Field ebf = editor.getClass().getDeclaredField("base");
      ebf.setAccessible(true);
      processing.app.Base base = (processing.app.Base)ebf.get(editor);
      List<JMenu> boardsCustomMenus;
      Field bcmf = base.getClass().getDeclaredField("boardsCustomMenus");
      bcmf.setAccessible(true);
      boardsCustomMenus = (List<JMenu>)bcmf.get(base);
      toActivate = findBoardInMenu(boardsCustomMenus.get(0),fqbn);
    } catch (Exception e) {
      e.printStackTrace();
    };
    return toActivate;
  }

  @SuppressWarnings("unchecked")
  protected static boolean activateBoard(String boardFqbn) {
    TargetBoard tbo = getBoard(boardFqbn);
    if (tbo == null) return false;
    JMenuItem toActivate = getMenuForBoard(tbo);
    try {
      if (toActivate != null) {
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
        System.out.println("Activated " + toActivate.getText() + ":" + boardFqbn);
        List<JMenu> boardsCustomMenus;
        Field ebf = editor.getClass().getDeclaredField("base");
        ebf.setAccessible(true);
        processing.app.Base base = (processing.app.Base)ebf.get(editor);
        Field bcmf = base.getClass().getDeclaredField("boardsCustomMenus");
        bcmf.setAccessible(true);
        boardsCustomMenus = (List<JMenu>)bcmf.get(base);
        TargetPlatform platform = tbo.getContainerPlatform();
        PreferencesMap customMenus = platform.getCustomMenus();
        String platUID = platform.getId() + "_" + platform.getFolder();
        if (cfs.length == 4) {
          String bid = cfs[2] + "_"; // board id
          for (String nvpair : cfs[3].split(",")) {
            String[] nv = nvpair.split("=",2);
            if (nv.length == 2 
                && !(nv[0].equals("") || nv[1].equals("") || nv[0].equals("CONSOLEBAUD"))
                && tbo.hasMenu(nv[0])) {
              JRadioButtonMenuItem rbmi = null;
              String tit = tr(customMenus.get(nv[0]));
              // System.out.println("custom_" + nv[0] + "=" + bid + nv[1] + " menu:" + tit);
              for (int i = 1; i < boardsCustomMenus.size() && rbmi == null;i++) {
                JMenu jm = boardsCustomMenus.get(i);
                if (jm.isVisible() 
                    && platUID.equals(jm.getClientProperty("platform")) 
                    && jm.getText().startsWith(tit)) {
                  for (int n = 0; n < jm.getItemCount() && rbmi == null; n++) {
                    JMenuItem jmi = jm.getItem(n);
                    if (jmi instanceof JRadioButtonMenuItem && jmi.isVisible()) {
                      try {
                        Action a = jmi.getAction();
                        String x = (String)a.getValue("custom_menu_option");
                        if (x != null && x.equals(nv[1])) {
                          rbmi = (JRadioButtonMenuItem)jmi;
                          //System.out.println(tit + "=" + rbmi.getText());
                          jmi.setSelected(true);
                          a.actionPerformed(ev);
                        }
                      } catch (Exception ex) { ex.printStackTrace();}
                    }
                  }
                }
              }
              if (rbmi == null) 
                System.out.println("Not found menu for custom_" + nv[0] + "=" + bid + nv[1] + " (" + tit + ")");
            }
          }
        }

        return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  } // activateBoard
  
  protected String prefsForBoard() {
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
  
  protected static void doInoParse(int action, IIPanel panel, 
                 String addName, String addFqbs, String removeName) {
    EditorTab etab = editor.findTab(editor.getSketch().getPrimaryFile());
    JTextArea sta = (JTextArea) etab.getTextArea();
    javax.swing.text.Document doc = sta.getDocument();
    int i = 0,line = 0,stoff = -1,enoff = -1,eofc = -1,eol = 0,bol = 0;
    int rmbeg = -1,rmend = -1;
    int nleft = doc.getLength();
    //System.out.println("docLength="+nleft);
    Matcher meoc = Pattern.compile("\\s+\\*/$").matcher(""),
            mbob = Pattern.compile("\\s*/\\* Boards:.*").matcher(""),
            mbol = Pattern.compile("[\\*\\s]+-\\s*([^\\*]*)\\*\\s*(.*)").matcher("");
    try {
      for (line = 0;eol < nleft;line++) {
        eol = sta.getLineEndOffset(line);
        if (eol > (bol + 1)) { // no empty line
          String lntx = doc.getText(bol, eol - bol - 1);
          meoc.reset(lntx);
          if (meoc.matches()) {
            if (eofc < 0) {
              eofc = eol;
              //System.out.println("eofc ln:"+line+"("+bol+".."+eol+"):"+lntx);
            }
            if (stoff >= 0 && enoff < 0) {
              enoff = bol;
              //System.out.println("enoff ln:"+line+"("+bol+".."+eol+"):"+lntx);
            }
          }
          if (stoff < 0) {
            mbob.reset(lntx);
            if (mbob.matches()) {
              stoff = eol;
              //System.out.println("stoff ln:"+line+"("+bol+".."+eol+"):"+lntx);
            }
          } else if (enoff < 0) {
            // in Boards comment
            mbol.reset(lntx);
            if (mbol.matches()) {
              String fqbs = mbol.group(2).trim(),
                     bnam = mbol.group(1).trim();
              JMenuItem boarditem = getMenuForBoard(getBoard(fqbs));
              if (action == 0) {
                panel.addInoCfg(fqbs,bnam,boarditem != null);
              } else if (action == 1 && rmbeg < 0 
                         && removeName != null 
                         && bnam.equals(removeName)) {
                rmbeg = bol; rmend = eol - 1;
              } else if (action == 2 && boarditem != null) {
                System.out.println("Activate board at line "+line+":"+bnam+" * "+fqbs);
                //                 +" inst="+(boarditem!=null?boarditem.getText():"No"));
                activateBoard(fqbs);
                return;
              }
            //} else {
            //  System.out.println("bad line:"+line+":"+lntx);
            }
          }
        }
        bol = eol;
        if (enoff >= 0) break; // Boards comment end.
        // only first 150 lines check
        // max 4000 bytes in Board comment
        // don't look for boards if end of first comment found
        if (line > 150 && (   (stoff > 0 && (eol - stoff) > 4000)
                           || (stoff < 0 && eofc >= 0)))
          break;
      }
      if (action == 0 || action == 2) return; // readed no remove/add ok
      if (removeName != null && rmbeg >= 0) {
        // replace / remove found
        doc.remove(rmbeg,rmend - rmbeg);
        if (addName != null && addFqbs != null)
          doc.insertString(rmbeg," * - " + addName.replace("*"," ").trim() 
                               + " * " + addFqbs.trim(),null);
        return;
      }
      // No remove found or need - Add
      if (enoff >= 0) {
        bol = enoff;
      } else if (stoff > 0) {
        bol = stoff;
      } else {
        bol = (eofc < 0 ? 0 : eofc);
        doc.insertString(bol,"/* Boards:\n */\n",null); // new Comment
        bol += 11;
      }
      doc.insertString(bol," * - " + addName.replace("*"," ").trim() + " * " + addFqbs.trim() + "\n",null);
    } catch (javax.swing.text.BadLocationException e) {}
  }
  
  protected static void doInoParse(String addName, String addFqbs, String removeName) {
    doInoParse(1,null,addName,addFqbs,removeName);
  }
  
  protected static void doInoParse(String addName,String addFqbs) {
    doInoParse(1,null,addName,addFqbs,null);
  }
  
  protected static void doInoParse(String removeName) {
    doInoParse(1,null,null,null,removeName);
  }
  
  protected static void doInoParse(IIPanel panel) {
    doInoParse(0,panel,null,null,null);
  }
  
  @Override
  public void run() {
    /* tool menu selected */
    if (panel == null) 
      try {
        startTimestamp = System.currentTimeMillis();
        panel = new IIPanel();
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
    try {
      String pfb = prefsForBoard();
      JMenuItem bmi = getMenuForBoard(getBoard(pfb));
      panel.setActual(pfb,(bmi != null ? bmi.getText() : "Unknown"));
      panel.removeInoCfgs();
      doInoParse(panel);
      dialog = new JDialog(editor,"IniIno",ModalityType.APPLICATION_MODAL);
      int scale = Theme.getScale();
      dialog.setTitle("IniIno");
      dialog.setResizable(true);
      dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      dialog.setBounds(100 * scale / 100, 100 * scale / 100, 500 * scale / 100, 520 * scale / 100);
      dialog.setContentPane(panel);
      dialog.pack();
      dialog.setVisible(true);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static void dialogClose() {
    if (dialog != null)
      dialog.setVisible(false);
  }

  static final class AfterLoadSketch implements Runnable {
    public void run() {
      //System.out.println("AfterLoadSketch invoked");
      try {
        if (editor.getSketch()!=null && editor.getSketch().getPrimaryFile()!=null) {
          if (PreferencesData.getBoolean("iniino.autostart",false)) {
            IniIno.doInoParse(2,null,null,null,null);
          }
          return; // OK no Invoke
        }
      } catch (Exception e) {}
      try {Thread.sleep(300);} catch (Exception e) {}
      SwingUtilities.invokeLater(this);
    }
  }
  
  public static Runnable waitSketch() {
    if (afterSkechLoad==null)
      afterSkechLoad = new AfterLoadSketch();
    return afterSkechLoad;
  }
  
  @Override
  public String getMenuTitle() {
    try {
      SwingUtilities.invokeAndWait(IniIno.waitSketch());
    } catch (Exception e) {
    } catch (java.lang.Error er) {
      /* load second sketch Cannot call invokeAndWait from the event dispatcher thread */
      //System.out.println("Second sketch?");
    }
    return "Project boards...";
  }

  @Override
  public void init(Editor editor) {
    IniIno.editor = editor;
    //System.out.println("init called");
  }
}
