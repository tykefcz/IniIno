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
import com.google.tykefcz.artools.BoardsUtil;
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
  protected int boardsInInoCount = 0;
  protected Component myMenu = null;
  
  public final static String MENUNAME = "Project boards...";
  
  protected void doInoParse(int action, IIPanel panel, 
                 String addName, String addFqbs, String removeName) {
    EditorTab etab = editor.findTab(editor.getSketch().getPrimaryFile());
    JTextArea sta = (JTextArea) etab.getTextArea();
    javax.swing.text.Document doc = sta.getDocument();
    int i = 0,line = 0,stoff = -1,enoff = -1,eofc = -1,eol = 0,bol = 0;
    int rmbeg = -1,rmend = -1,swbeg = -1, swend = -1;
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
              JMenuItem boarditem = BoardsUtil.getMenuForBoard(base,BoardsUtil.getBoard(fqbs));
              if (action == 0) {
                panel.addInoCfg(fqbs,bnam,boarditem != null);
              } else if (action == 1 && rmbeg < 0 
                         && removeName != null 
                         && bnam.equals(removeName)) {
                rmbeg = bol; rmend = eol;
              } else if (action == 4 && bnam.equals(removeName)
                         && swbeg >= 0 && swend >= 0) {
                System.out.println("Swap Up board at line " + line + ":" + bnam);
                String swline = doc.getText(swbeg, swend - swbeg);
                lntx = doc.getText(bol, eol - bol);
                doc.remove(bol,eol - bol);
                doc.insertString(bol,swline,null);
                doc.remove(swbeg, swend - swbeg);
                doc.insertString(swbeg,lntx,null);
                return;
              } else if (action == 2 && boarditem != null) {
                System.out.println("Activate board at line "+line+":"+bnam+" * "+fqbs);
                //                 +" inst="+(boarditem!=null?boarditem.getText():"No"));
                BoardsUtil.activateBoard(fqbs);
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
              swbeg = bol; swend = eol;
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
  
  protected void doInoSwap(String upName) {
    doInoParse(4,null,null,null,upName);
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
            JMenuItem boarditem = BoardsUtil.getMenuForBoard(base,BoardsUtil.getBoard(aa[1].trim()));
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
      String pfb = BoardsUtil.prefsForBoard();
      JMenuItem bmi = BoardsUtil.getMenuForBoard(base,BoardsUtil.getBoard(pfb));
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
      if (toolsMenu != null)
        return;
      System.out.println("Refreshing board menus...");
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
      myMenu = null;
      System.out.println("Can't find Tools menu ");
      return;
    }
    int i=0;
    for (Object o1 : toolsMenu.getSubElements()) {
      if (o1 instanceof JMenu) {
        JMenu jm=(JMenu)o1;
        if (jm.getText().equals(IniIno.MENUNAME)) {
          myMenu = (Component)o1;
          break;
        }
      } else if (o1 instanceof JMenuItem) {
        JMenuItem jm=(JMenuItem)o1;
        if (jm.getText().equals(IniIno.MENUNAME)) {
          myMenu = (Component)o1;
          break;
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
          BoardsUtil.activateBoard(fqbs);
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
