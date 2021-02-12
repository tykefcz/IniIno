/*
 * (c) 2021 Jiri Gabriel <tykefcz@gmail.com>
 * This code is licensed under MIT license (see license.txt for details)
 */

package com.google.tykefcz.iniino;

import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.MenuElement;
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

  protected Editor editor;
  protected Base base;
  protected IIPanel panel = null;
  protected JDialog dialog = null;
  protected JPopupMenu toolsMenu = null;
  protected JMenu boardsMenu = null;
  protected int boardsInInoCount = 0;
  protected Component myMenu = null;
  
  public final static String MENUNAME = "Project boards...";
  
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
  protected JMenuItem getMenuForBoard(TargetBoard tbo) {
    JMenuItem toActivate = null;
    try {
      if (tbo == null || base==null) return null;
      String fqbn = boardFqbn(tbo);
      toActivate = findBoardInMenu(base.getBoardsCustomMenus().get(0),fqbn);
    } catch (Exception ex) {
      ex.printStackTrace();
    };
    return toActivate;
  }

  // return: 0 - OK, 1 - warning, 2 - error
  @SuppressWarnings("unchecked")
  protected int activateBoard(String boardFqbn) {
    TargetBoard tbo = getBoard(boardFqbn);
    int warncount = 0;
    if (tbo == null) return 2;
    JMenuItem toActivate = getMenuForBoard(tbo);
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
  
  protected void doInoParse(int action, IIPanel panel, 
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
                rmbeg = bol; rmend = eol;
              } else if (action == 2 && boarditem != null) {
                System.out.println("Activate board at line "+line+":"+bnam+" * "+fqbs);
                //                 +" inst="+(boarditem!=null?boarditem.getText():"No"));
                activateBoard(fqbs);
                return;
              } else if (action == 3) {
                JMenuItem mi = 
                      new JMenuItem(
                        new SwitchBoardAction(bnam,fqbs));
                mi.setEnabled(boarditem != null);
                ((JMenu)myMenu).add(mi);
                boardsInInoCount++;
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
      if (action != 1) return; // readed no remove/add
      if (removeName != null && rmbeg >= 0) {
        // replace / remove found
        doc.remove(rmbeg,rmend - rmbeg);
        if (myMenu != null && myMenu instanceof JMenu) {
          JMenu mm = (JMenu)myMenu;
          int menuremove=-1;
          int mi = mm.getMenuComponentCount() - 1;
          while (mi > 0) {
            if (mm.getMenuComponent(mi) instanceof JMenuItem 
                && ((JMenuItem)mm.getMenuComponent(mi)).getText().equals(removeName)) {
              menuremove = mi;
            }
            mi--;
          }
          if (menuremove > 0)
            mm.remove(menuremove);
          if (addName != null && addFqbs != null)
          try {
            JMenuItem nmi = new JMenuItem(
                new SwitchBoardAction(addName,addFqbs));
            if (menuremove <= 0) {
              if (boardsInInoCount==0)
                mm.insertSeparator(1);
              boardsInInoCount++;
              mm.insert(nmi,1 + boardsInInoCount);
            } else {
              mm.insert(nmi,menuremove);
            }
          } catch(Exception ex2) {}
        }
        if (addName != null && addFqbs != null)
          doc.insertString(rmbeg," * - " + addName.replace("*"," ").trim() 
                               + " * " + addFqbs.trim() + "\n",null);
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
    } catch (javax.swing.text.BadLocationException ex) {}
  }
  
  protected void doInoParse(String addName, String addFqbs, String removeName) {
    doInoParse(1,null,addName,addFqbs,removeName);
  }
  
  protected void doInoParse(String addName,String addFqbs) {
    doInoParse(1,null,addName,addFqbs,null);
  }
  
  protected void doInoParse(String removeName) {
    doInoParse(1,null,null,null,removeName);
  }
  
  protected void doInoParse(IIPanel panel) {
    doInoParse(0,panel,null,null,null);
  }
  
  protected void doFavParse(IIPanel panel,JMenu mm) {
    int i = 0;
    String s;
    while (i < 10) {
      s = "tool.iniino.preset." + String.valueOf(i);
      if (PreferencesData.has(s)) {
        s=PreferencesData.get(s);
        if (s!=null && !s.trim().equals("")) {
          String[] aa = s.split("\\*",2);
          if (aa.length == 2) {
            //System.out.println("adding " + i + ":" + aa[0]);
            JMenuItem boarditem = getMenuForBoard(getBoard(aa[1].trim()));
            if (mm!=null) {
              JMenuItem x = new JMenuItem(
                new SwitchBoardAction(aa[0].trim(),aa[1].trim()));
              x.setEnabled(boarditem != null);
              mm.add(x);
            } else if (panel != null)
              panel.addFavCfg(aa[1].trim(),aa[0].trim(),boarditem != null);
          }
        }
      }
      i++;
    }
  }
  
  @Override
  public void run() {
    /* tool menu selected */
    if (panel == null) 
      try {
        panel = new IIPanel(this);
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
    try {
      String pfb = prefsForBoard();
      JMenuItem bmi = getMenuForBoard(getBoard(pfb));
      panel.removeInoCfgs();
      panel.setActual(pfb,(bmi != null ? bmi.getText() : "Unknown"));
      doInoParse(panel);
      doFavParse(panel,null);
      dialog = new JDialog(editor,"IniIno",ModalityType.APPLICATION_MODAL);
      int scale = Theme.getScale();
      panel.setMainDialog(dialog);
      dialog.setTitle("IniIno");
      dialog.setResizable(true);
      dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      dialog.setBounds(100 * scale / 100, 100 * scale / 100, 500 * scale / 100, 520 * scale / 100);
      dialog.setContentPane(panel);
      dialog.pack();
      dialog.setVisible(true);
      refreshMyMenu();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
  
  private Component topParent(Component x) {
    Component rv = x, lastok = x;
    int lev=0;
    while (rv!=null) {
      lastok = rv;
      if (rv instanceof JPopupMenu)
        rv = ((JPopupMenu)rv).getInvoker();
      else 
        rv=rv.getParent();
      System.out.println("par:" + (lev++) + ":" + lastok.getClass().toString() + 
                         (rv == null ? "END" : "..."));
    }
    return lastok;
  }

  public void readMenus(boolean first_read) {
    if (!first_read) {
      if (toolsMenu != null && boardsMenu != null)
        return;
      System.out.println("Refreshing board menus...");
      boardsMenu = null;
      toolsMenu = null;
      myMenu = null;
    }
    try {
      for (Object o1 : editor.getContentPane().getParent().getComponents()) {
        if (o1 instanceof JMenuBar) {
          for (MenuElement o2 : ((JMenuBar)o1).getSubElements()) {
            if (o2 instanceof JMenu && ((JMenu)o2).getText().equals(tr("Tools"))) {
              MenuElement o3 = ((JMenu) o2).getSubElements()[0];
              if (o3 instanceof JPopupMenu) {
                toolsMenu = (JPopupMenu) o3;
                break;
              }
            }
          }
          break;
        }
      }
    } catch (Exception ex) {}
    if (toolsMenu == null) {
      boardsMenu = null;
      myMenu = null;
      System.out.println("Can't find Tools menu ");
      return;
    }
    int i=0;
    for (Object o1 : toolsMenu.getSubElements()) {
      if (o1 instanceof JMenu) {
        JMenu jm=(JMenu)o1;
        if (jm.getText().startsWith(tr("Board"))) {
          boardsMenu = (JMenu)o1;
          // System.out.println("Found Board menu " + ((JMenu)o1).getText());
          if (myMenu != null) break;
        } else if (jm.getText().equals(IniIno.MENUNAME)) {
          myMenu = (Component)o1;
          if (boardsMenu != null) break;
        }
      } else if (o1 instanceof JMenuItem) {
        JMenuItem jm=(JMenuItem)o1;
        if (jm.getText().equals(IniIno.MENUNAME)) {
          myMenu = (Component)o1;
          if (boardsMenu != null) break;
        }
      }
      i++;
    }
    if (myMenu == null) {
      System.out.println("Can't find Tools -> " + IniIno.MENUNAME);
    } else {
      for (i = 0; i < toolsMenu.getComponentCount(); i++) {
        if (toolsMenu.getComponent(i) == myMenu) {
          if (myMenu instanceof JMenuItem) {
            JMenu myNewMenu = new JMenu(IniIno.MENUNAME);
            //myNewMenu.putClientProperty("removeOnWindowDeactivation", true);
            ((JMenuItem)myMenu).setText("Manage in sketch boards");
            myNewMenu.add((JMenuItem)myMenu);
            toolsMenu.remove(i);
            toolsMenu.insert(myNewMenu,i);
            myMenu = myNewMenu;
          }
          break;
        }
      }
    }
  }

  public final class SwitchBoardAction extends AbstractAction {
    public SwitchBoardAction(String name, String fqbs) {
      this.putValue(Action.NAME,name);
      this.putValue("fqbs",fqbs);
    }
    public void actionPerformed(ActionEvent e) {
      // System.out.println("activating " + getValue("fqbs"));
      try {
        String fqbs = (String)getValue("fqbs");
        if (fqbs != null)
          activateBoard(fqbs);
      } catch (Exception ex) {}
    }
  }

  protected void refreshMyMenu() {
    if (myMenu != null && myMenu instanceof JMenu) try {
      JMenu mm = (JMenu)myMenu;
      int i = mm.getMenuComponentCount() - 1;
      while (i > 0) {
        //System.out.println("comp(" + i + ") = " + mm.getMenuComponent(i).getClass());
        if (mm.getMenuComponent(i) instanceof JMenuItem 
           || mm.getMenuComponent(i) instanceof JSeparator) {
          mm.remove(i);
        }
        i--;
      }
      //mm.add(new JMenuItem(new SwitchBoardAction("tst2","neco:jineho:tam_bude")));
      mm.addSeparator();
      boardsInInoCount=0;
      doInoParse(3,null,null,null,null);
      if (boardsInInoCount > 0) 
        mm.addSeparator();
      doFavParse((IIPanel) null,mm);
    } catch (Exception e) {
      e.printStackTrace();
    }
    //System.out.println("refresh end");
  }

  final class AfterLoadSketch extends Thread {
    private IniIno caller;
    public AfterLoadSketch(IniIno c) { 
      caller=c; 
    }
    public void run() {
      boolean done=false;
      while (!done) {
        try {Thread.sleep(300);} catch (Exception e) {}
        if (Base.INSTANCE == null) continue;
        //System.out.println("base:" + Base.INSTANCE.toString() + " hash:" + Base.INSTANCE.hashCode());
        caller.base = Base.INSTANCE;
        try {
          if (caller.editor.getSketch()!=null && caller.editor.getSketch().getPrimaryFile()!=null) {
            caller.readMenus(true);
            if (caller.base.getEditors().size() == 1
              && PreferencesData.getBoolean("tool.iniino.autostart",false)) {
              //System.out.println("first edit / first board");
              doInoParse(2,null,null,null,null);
            }
            caller.refreshMyMenu();
            done = true; // OK end thread
          }
        } catch (Exception e3) {}
      }
      //System.out.println("end wait for load runner");
    }
  }

  @Override
  public String getMenuTitle() {
    try {
      new AfterLoadSketch(this).start();
    } catch (Exception e) {
    } catch (java.lang.Error er) {
      /* load second sketch Cannot call invokeAndWait from the event dispatcher thread */
      //System.out.println("Second sketch?");
    }
    return IniIno.MENUNAME;
  }

  @Override
  public void init(Editor editor) {
    this.editor = editor;
    //System.out.println("init called");
  }
}
