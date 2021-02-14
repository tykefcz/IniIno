/*
 * (c) 2021 Jiri Gabriel <tykefcz@gmail.com>
 * This code is licensed under MIT license (see license.txt for details)
 */

package com.google.tykefcz.iniino;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import processing.app.Base;
import processing.app.Editor;
import processing.app.PreferencesData;
import processing.app.Theme;

@SuppressWarnings("serial")

public class IIPanel extends JPanel 
                  implements ActionListener,ListSelectionListener {
  private IniIno iniino;
  private JDialog dialog;
  private JPanel pnP;
  private JList<String> lsInIno;
  private JList<String> lsInFav;
  private ListData dataInIno;
  private ListData dataInFav;
  private JTextArea taActual;
  private JButton btAddIno;
  private JButton btRepIno;
  private JButton btActivate;
  private JButton btClose;
  private JButton btAddFav;
  private JButton btDelFi;
  private JButton btRenFi;
  private JButton btIno2F;
  private JButton btFav2I;
  private JButton btFavUp;
  private JTextField tfFavName;
  private JTextArea taRaw;
  private JCheckBox cbAl;
  private String strRaw = "", actSet = "", actLbl = "Unknown";
  private int selId, foundId, favSelId, favFoundId;
  private boolean saveFav=false;
  //private DefaultListModel<String> defmodel;
  
  private class BoardSet {
    public String name;
    public String cfg;
    public boolean installed;
    public BoardSet() { this("","",false);}
    public BoardSet(String cf, String nm, boolean inst) {
      name = nm; cfg = cf; installed = inst; }
  }

  private class ListData implements ListModel<String> {
    private String actSet;
    private List<BoardSet> list;
    private List<ListDataListener> listeners;
    public ListData() {
      list = new ArrayList<BoardSet>();
      listeners = new ArrayList<ListDataListener>();
      actSet="";
    }
    public ListData(String actual) {
      this();
      setActual(actual);
    }
    public void setActual(String actual) {
      actSet = (actual==null?"":actual);
    }
    public int getSize() { return list.size(); }
    public String getElementAt(int n) {
      BoardSet bs=null;
      try { bs=list.get(n); } 
      catch (Exception e) {}
      if (bs == null) return "";
      String addon = "";
      if (!bs.installed) addon = " (not available)";
      else if (bs.cfg.equals(actSet)) addon = " (selected)";
      return bs.name + addon;
    }
    public BoardSet get(int i) { 
      BoardSet bs=null;
      try { bs=list.get(i); }
      catch (Exception e) {}
      return bs;
    }
    public void clear() { 
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.INTERVAL_REMOVED,0,list.size());
      list.clear();
      for (ListDataListener l : listeners) l.intervalRemoved(e);
    }
    public void add(BoardSet x) {
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.INTERVAL_ADDED,list.size(),list.size());
      list.add(x);
      for (ListDataListener l : listeners) l.intervalAdded(e);
    }
    public void remove(int i) {
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.INTERVAL_REMOVED,i,i);
      if (i >= 0 && i <= list.size() && list.get(i) != null) {
        list.remove(i);
        for (ListDataListener l : listeners) l.intervalRemoved(e);
      }
    }
    public void replace(int i, String nn, String ncf) { 
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.CONTENTS_CHANGED,i,i);
      BoardSet x = null;
      try { x=list.get(i); } catch (Exception ex) {}
      if (x != null && nn != null) x.name = nn;
      if (x != null && ncf != null) x.cfg = ncf;
      for (ListDataListener l : listeners) l.contentsChanged(e);
    }
    public boolean swap(int i1, int i2) {
      int n=list.size();
      if (i1 < 0 || i1 >= n || i2 < 0 || i2 > n)  return false;
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.CONTENTS_CHANGED,i1<=i2?i1:i2,i2<=i1?i2:i1);
      Collections.swap(list, i1, i2);
      for (ListDataListener l : listeners) l.contentsChanged(e);
      return true;
    }
    public boolean has(String fqbs) {
      for (int i=0; i<list.size(); i++) {
        if (list.get(i).cfg.equals(fqbs))
          return true;
      }
      return false;
    }
    public void addListDataListener(ListDataListener l) { listeners.add(l); }
    public void removeListDataListener(ListDataListener l) { listeners.remove(l); }
  }
    
  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    if (src == btActivate) {
      int rv;
      if ((rv=iniino.activateBoard(taRaw.getText()))<2) {
        JOptionPane.showMessageDialog(btActivate, 
          rv==0?"Activated successfully":"Activated with Warning" );
        dialogClose();
      } else
        JOptionPane.showMessageDialog(btActivate, "Error activate");
    } else if (src == btAddIno) {
      if (!actSet.equals("") && foundId < 0) {
        iniino.doInoParse(actLbl,actSet);
        addInoCfg(actSet,actLbl,true);
      }
      btAddIno.setEnabled(false);
    } else if (src == btRepIno) {
      if (!actSet.equals("") && foundId < 0 && selId >= 0) {
        BoardSet x = dataInIno.get(selId);
        String newname = x.name;
        String[] a = actSet.split(":",4),
                 b = x.cfg.split(":",4);
        if (a.length < 3 || b.length < 3 
            || !(a[0].equals(b[0]) && a[1].equals(b[1]) && a[2].equals(b[2])))
          newname = actLbl;
        iniino.doInoParse(newname,actSet,x.name);
        foundId = selId;
        dataInIno.replace(selId,newname,actSet);
        taRaw.setText(actSet);
        taRaw.setCaretPosition(0);
        btAddIno.setEnabled(false);
      }
      btRepIno.setEnabled(false);
    } else if (src == btRenFi) { // Rename entry
      //System.out.println("rename " + selId + "/" + favSelId + " to '" + tfFavName.getText() + "'");
      String newn=tfFavName.getText().trim();
      newn = newn.replaceAll("[\\*-;\\n\\t\\r=/\\\\]","");
      tfFavName.setText(newn);
      if (!newn.equals("")) {
        BoardSet bs;
        String oldn;
        if (selId >= 0) {
          bs = dataInIno.get(selId);
          oldn = bs.name;
          //System.out.println("rename in Ino " + oldn + " -> " + newn);
          iniino.doInoParse(newn, bs.cfg, oldn);
          dataInIno.replace(selId,newn,null);
        } else if (favSelId >= 0) {
          bs = dataInFav.get(favSelId);
          oldn = bs.name;
          //System.out.println("rename in Fav " + oldn + " -> " + newn);
          dataInFav.replace(favSelId,newn,null);
          saveFav=true;
        }
      }
    } else if (src == btAddFav) {
      if (favFoundId < 0 && dataInFav.getSize() < 10) {
        dataInFav.add(new BoardSet(actSet,actLbl,true));
        favFoundId = dataInFav.getSize() - 1;
        btAddFav.setEnabled(false);
        saveFav=true;
      }
    } else if (src == btIno2F) {
      if (selId >= 0) {
        BoardSet bs = dataInIno.get(selId);
        if (bs!=null && !dataInFav.has(bs.cfg)) {
          dataInFav.add(new BoardSet(bs.cfg,bs.name,bs.installed));
          saveFav=true;
        }
      }
    } else if (src == btFav2I) {
      if (favSelId >= 0) {
        BoardSet bs = dataInFav.get(favSelId);
        if (bs!=null && !dataInIno.has(bs.cfg)) {
          iniino.doInoParse(bs.name,bs.cfg);
          addInoCfg(bs.cfg,bs.name,bs.installed);
        }
      }
    } else if (src == btFavUp) {
      if (favSelId > 0) {
        int oldsel=favSelId;
        if (dataInFav.swap(oldsel - 1, oldsel)) {
          lsInFav.clearSelection();
          lsInFav.setSelectedIndex(oldsel - 1);
          saveFav=true;
        }
      } else if (selId > 0) {
        int oldsel = selId;
        if (dataInIno.swap(oldsel - 1, oldsel)) {
          BoardSet bs = dataInIno.get(oldsel - 1);
          if (bs!=null) 
            iniino.doInoSwap(bs.name);
          lsInIno.clearSelection();
          lsInIno.setSelectedIndex(oldsel - 1);
        }
      }
    } else if (src == btDelFi) {
      int id;
      if (favSelId >= 0) {
        id=favSelId;
        dataInFav.remove(id);
        favSelId=-1;
        if (favFoundId == id)
          favFoundId = -1;
        else if (favFoundId > id)
          favFoundId--;
        lsInFav.clearSelection();
        saveFav=true;
      } else if (selId >= 0) {
        BoardSet x = dataInIno.get(selId);
        if (x!=null) {
          id = selId;
          iniino.doInoParse(x.name);
          if (foundId == id)
            foundId = -1;
          else if (foundId > id)
            foundId--;
          dataInIno.remove(id);
          selId=-1;
          lsInIno.clearSelection();
        }
      }
    } else if (src == btClose) {
      dialogClose();
    } else if (src == cbAl) {
      PreferencesData.setBoolean("iniino.autostart",cbAl.isSelected());
    }
  }

  public void dialogClose() {
    if (dialog != null) {
      if (saveFav) {
        //System.out.println("Save favorites");
        int i = 0;
        String s;
        BoardSet bs;
        while (i < 10) {
          s = "tool.iniino.preset." + String.valueOf(i);
          bs = dataInFav.get(i);
          if (bs != null) {
            PreferencesData.set(s,bs.name.trim() + "*" + bs.cfg.trim());
          } else if (PreferencesData.has(s)) {
            PreferencesData.remove(s);
          }
          i++;
        }
      }
      dialog.setVisible(false);
    }
  }

  public void valueChanged(ListSelectionEvent e) { 
    Object src = e.getSource();
    if (src==lsInIno || src==lsInFav)
      selectIni(e); 
  }

  public void setMainDialog(JDialog d) {
    dialog = d;
  }

  public IIPanel(IniIno caller) {
    iniino = caller;
    GridBagLayout g = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    this.setLayout(g);

    c.gridx = 0; c.gridy = 0; c.gridwidth = 5; c.gridheight = 1;
    c.fill = GridBagConstraints.BOTH; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(20, 10, 0, 0);
    JLabel lbA = new JLabel("Actual board:");
    g.setConstraints(lbA, c);
    this.add(lbA);
    
    c.gridy++; c.gridwidth = 5;
    c.fill = GridBagConstraints.BOTH;c.weightx = 1; c.weighty = 1;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(0, 10, 0, 10);
    taActual = new JTextArea();
    taActual.setRows(2);
    JScrollPane scpTaAct = new JScrollPane(taActual);
    g.setConstraints(scpTaAct, c); this.add(scpTaAct);

    c.gridx=0; c.gridy++; c.gridwidth = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 20, 10, 10);
    btAddIno = new JButton("Add to project");
    g.setConstraints(btAddIno, c);
    this.add(btAddIno);
    
    c.gridx++; c.gridwidth = 1;
    c.fill = GridBagConstraints.NONE;c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 10, 10, 20);
    btRepIno = new JButton("Replace in project");
    g.setConstraints(btRepIno, c);
    this.add(btRepIno);

    c.gridx+=2; c.gridwidth = 1;
    c.fill = GridBagConstraints.VERTICAL;c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHEAST;
    c.insets = new Insets(10, 10, 10, 20);
    btAddFav = new JButton("Add to Favorites");
    g.setConstraints(btAddFav, c);
    this.add(btAddFav);


    c.gridx = 0; c.gridy++; c.gridwidth = 2;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 10, 0, 0);
    JLabel lbB = new JLabel("Boards in project:");
    g.setConstraints(lbB, c);
    this.add(lbB);

    c.gridx+=3; c.gridwidth = 2; c.gridheight = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 10, 0, 0);
    lbB = new JLabel("Favorite Boards:");
    g.setConstraints(lbB, c);
    this.add(lbB);

    c.gridx=0; c.gridy++; c.gridwidth = 2; c.gridheight = 5;
    c.fill = GridBagConstraints.BOTH; c.weightx = 2; c.weighty = 5;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(0, 10, 0, 10);
    dataInIno = new ListData();
    lsInIno = new JList<String>(dataInIno);
    lsInIno.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scpInIno = new JScrollPane(lsInIno);
    g.setConstraints(scpInIno, c);
    this.add(scpInIno);

    int savedy = c.gridy;

    c.gridx+=2; c.gridwidth = 1; c.gridheight = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(10, 0, 0, 0);
    btFavUp = new JButton("^");
    g.setConstraints(btFavUp, c);
    this.add(btFavUp);

    c.gridy++;
    btIno2F = new JButton(">");
    g.setConstraints(btIno2F, c);
    this.add(btIno2F);

    c.gridy++;
    btFav2I = new JButton("<");
    g.setConstraints(btFav2I, c);
    this.add(btFav2I);

    c.gridy++;
    btDelFi = new JButton("Del");
    g.setConstraints(btDelFi, c);
    this.add(btDelFi);
    
    c.gridx=3; c.gridy=savedy; c.gridwidth = 2; c.gridheight=5;
    c.fill = GridBagConstraints.BOTH; c.weightx = 2; c.weighty = 5;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(0, 10, 0, 10);
    dataInFav = new ListData();
    lsInFav = new JList<String>(dataInFav);
    lsInFav.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scpInFav = new JScrollPane(lsInFav);
    g.setConstraints(scpInFav, c);
    this.add(scpInFav);
    
    c.gridx=0; c.gridy+=c.gridheight; c.gridwidth = 4; c.gridheight=1;
    c.fill = GridBagConstraints.VERTICAL; c.weightx = 3; c.weighty = 1;
    c.anchor = GridBagConstraints.WEST;
    //                 top left bottom right
    c.insets = new Insets(20, 10, 10, 0);
    tfFavName = new JTextField(30);
    g.setConstraints(tfFavName, c);this.add(tfFavName);

    c.gridx=4; c.gridwidth = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 1; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(20, 10, 10, 10);
    btRenFi = new JButton("Rename");
    g.setConstraints(btRenFi, c);
    this.add(btRenFi);

    c.gridx=0; c.gridy++; c.gridwidth = 5;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 10, 0, 0);
    cbAl = new JCheckBox("Auto activate first board after sketch load",
               PreferencesData.getBoolean("iniino.autostart",false));
    g.setConstraints(cbAl, c);
    this.add(cbAl);

    c.gridy++; c.gridwidth = 5;
    c.fill = GridBagConstraints.NONE; c.weightx = 0; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 10, 0, 0);
    JLabel lbC = new JLabel("Raw settings (for arduino-cli):");
    g.setConstraints(lbC, c);
    this.add(lbC);

    c.gridy++; c.gridwidth = 5;
    c.fill = GridBagConstraints.BOTH; c.weightx = 1; c.weighty = 1;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(0, 10, 0, 10);
    taRaw = new JTextArea(strRaw);
    taRaw.setRows(3);
    JScrollPane scpTaRaw = new JScrollPane(taRaw);
    g.setConstraints(scpTaRaw, c);this.add(scpTaRaw);
    
    c.gridy++; c.gridwidth = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 1; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(10, 20, 20, 10);
    btActivate = new JButton("Activate");
    g.setConstraints(btActivate, c);
    this.add(btActivate);
    
    c.gridx=4; c.gridwidth = 1;
    c.fill = GridBagConstraints.NONE; c.weightx = 1; c.weighty = 0;
    c.anchor = GridBagConstraints.NORTHEAST;
    c.insets = new Insets(10, 10, 20, 20);
    btClose = new JButton("Close");
    g.setConstraints(btClose, c);
    this.add(btClose);
    
    btActivate.addActionListener(this);
    btAddIno.addActionListener(this);
    btRepIno.addActionListener(this);
    btAddFav.addActionListener(this);
    btDelFi.addActionListener(this);
    btRenFi.addActionListener(this);
    btIno2F.addActionListener(this);
    btFav2I.addActionListener(this);
    btFavUp.addActionListener(this);
    btClose.addActionListener(this);
    cbAl.addActionListener(this);
    lsInIno.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lsInIno.addListSelectionListener(this);
    lsInFav.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lsInFav.addListSelectionListener(this);

    taActual.setEditable(false);
    taRaw.setEditable(false);
    
    defaultState();
  }

  public void defaultState() {
    btAddIno.setEnabled(false);
    btRepIno.setEnabled(false);
    btAddFav.setEnabled(false);
    btDelFi.setEnabled(false);
    btIno2F.setEnabled(false);
    btFav2I.setEnabled(false);
    btFavUp.setEnabled(false);
    btActivate.setEnabled(false);
    btRenFi.setEnabled(false);
    tfFavName.setText("");
    tfFavName.setEditable(false);
  }

  public void setActual(String as, String lbl) {
    actSet = as;
    actLbl = lbl;
    foundId = -1;
    taActual.setText(lbl + "\n" + as);
    taActual.setOpaque(true);
    btAddIno.setEnabled(true);
    btAddFav.setEnabled(true);
    dataInIno.setActual(as);
    dataInFav.setActual(as);
  }

  @SuppressWarnings("unchecked")
  public void selectIni(ListSelectionEvent e) {
    JList<String> lsObj=(JList<String>)e.getSource();
    taRaw.setText("");
    btRenFi.setEnabled(false);
    btDelFi.setEnabled(false);
    tfFavName.setText("");
    tfFavName.setEditable(false);
    selId = -1;
    favSelId = -1;
    try {
      int sel;
      sel = lsObj.getSelectedIndex();
      if (lsObj==lsInIno && sel >= 0 && sel < dataInIno.getSize()) {
        BoardSet x = dataInIno.get(sel);
        taRaw.setText(x.cfg);
        btActivate.setEnabled(x.installed && foundId != sel);
        btAddIno.setEnabled(foundId < 0);
        btRepIno.setEnabled(foundId < 0);
        btDelFi.setEnabled(true);
        btIno2F.setEnabled(!dataInFav.has(x.cfg));
        btFav2I.setEnabled(false);
        btFavUp.setEnabled(sel > 0);
        btRenFi.setEnabled(true);
        tfFavName.setText(x.name);
        tfFavName.setEditable(true);
        selId = sel;
        lsInFav.clearSelection();
      } else if (lsObj==lsInFav && sel >= 0 && sel < dataInFav.getSize()) {
        BoardSet x = dataInFav.get(sel);
        taRaw.setText(x.cfg);
        btActivate.setEnabled(x.installed && favFoundId != sel);
        btAddIno.setEnabled(false);
        btRepIno.setEnabled(false);
        btAddFav.setEnabled(favFoundId < 0);
        btDelFi.setEnabled(true);
        btRenFi.setEnabled(true);
        btIno2F.setEnabled(false);
        btFav2I.setEnabled(!dataInIno.has(x.cfg));
        btFavUp.setEnabled(sel > 0);
        tfFavName.setText(x.name);
        tfFavName.setEditable(true);
        lsInIno.clearSelection();
        favSelId = sel;
      }
      taRaw.setCaretPosition(0);
    } catch (Exception ex) {}
  }
  
  public void removeInoCfgs() {
    dataInIno.clear();
    dataInFav.clear();
    foundId = selId = favSelId = favFoundId = -1;
    defaultState();
  }
  
  public void addInoCfg(String cfg, String name, boolean installed) {
    dataInIno.add(new BoardSet(cfg,name,installed));
    if (cfg.equals(actSet)) {
      foundId = dataInIno.getSize() - 1;
      if (selId != foundId) {
        selId = foundId;
        btAddIno.setEnabled(false);
        btRepIno.setEnabled(false);
        lsInIno.clearSelection();
        lsInIno.setSelectedIndex(foundId);
        taRaw.setText(cfg);
        taRaw.setCaretPosition(0);
        btRenFi.setEnabled(true);
        tfFavName.setText(name);
        tfFavName.setEditable(true);
      }
    }
    if (btIno2F.isEnabled() && taRaw.getText().equals(cfg))
       btIno2F.setEnabled(false);
  }

  public void addFavCfg(String cfg, String name, boolean installed) {
    dataInFav.add(new BoardSet(cfg,name,installed));
    if (cfg.equals(actSet)) {
      favFoundId = dataInFav.getSize() - 1;
      btAddFav.setEnabled(false);
      if (favSelId != favFoundId && selId < 0) {
        favSelId = favFoundId;
        lsInFav.clearSelection();
        lsInFav.setSelectedIndex(favFoundId);
        taRaw.setText(cfg);
        taRaw.setCaretPosition(0);
        btRenFi.setEnabled(true);
        tfFavName.setText(name);
        tfFavName.setEditable(true);
      }
    }
    if (dataInFav.getSize() == 10)
      btAddFav.setEnabled(false);
  }
}
