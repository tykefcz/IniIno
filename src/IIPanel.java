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
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
import processing.app.Theme;

@SuppressWarnings("serial")

public class IIPanel extends JPanel {
  private JPanel pnP;
  private JList<String> lsInIno;
  private JTextArea taActual;
  private JButton btAddIno;
  private JButton btRepIno;
  private JButton btActivate;
  private JButton btClose;
  private JTextArea taRaw;
  private String strRaw = "", actSet = "", actLbl = "Unknown";
  private int selId, foundId;
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
    private List<BoardSet> list;
    private List<ListDataListener> listeners;
    public ListData() {
      list = new ArrayList<BoardSet>();
      listeners = new ArrayList<ListDataListener>(); }
    public int getSize() { return list.size(); }
    public String getElementAt(int n) {
      BoardSet bs=list.get(n);
      if (bs == null) return "";
      return bs.name + (bs.installed?"":" (not available)");
    }
    public BoardSet get(int i) { return list.get(i); }
    public void clear() { 
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.INTERVAL_REMOVED,0,list.size());
      list.clear();
      for (ListDataListener l : listeners)
        l.intervalRemoved(e);
    }
    public void add(BoardSet x) {
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.INTERVAL_ADDED,list.size(),list.size());
      list.add(x);
      for (ListDataListener l : listeners) l.intervalAdded(e);
    }
    public void replace(int i, String nn, String ncf) { 
      ListDataEvent e = new ListDataEvent(
          this,ListDataEvent.CONTENTS_CHANGED,i,i);
      BoardSet x = list.get(i);
      if (x != null && nn != null) x.name = nn;
      if (x != null && ncf != null) x.cfg = ncf;
      for (ListDataListener l : listeners) l.contentsChanged(e);
    }
    public void addListDataListener(ListDataListener l) { listeners.add(l); }
    public void removeListDataListener(ListDataListener l) { listeners.remove(l); }
  }
    
  private ListData dataInIno;

  public IIPanel() {
    GridBagLayout gbP = new GridBagLayout();
    GridBagConstraints gbcP = new GridBagConstraints();
    this.setLayout(gbP);

    gbcP.gridx = 0; gbcP.gridy = 0; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 0; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(20, 10, 0, 0);
    JLabel lbA = new JLabel("Actual board:");
    gbP.setConstraints(lbA, gbcP);
    this.add(lbA);
    
    gbcP.gridx = 0; gbcP.gridy = 1; gbcP.gridwidth = 2; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.BOTH;gbcP.weightx = 1; gbcP.weighty = 1;
    gbcP.anchor = GridBagConstraints.CENTER;
    gbcP.insets = new Insets(0, 10, 0, 10);
    taActual = new JTextArea();
    taActual.setRows(2);
    JScrollPane scpTaAct = new JScrollPane(taActual);
    gbP.setConstraints(scpTaAct, gbcP); this.add(scpTaAct);

    gbcP.gridx = 0; gbcP.gridy = 2; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 0; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(10, 20, 10, 10);
    btAddIno = new JButton("Add to project");
    gbP.setConstraints(btAddIno, gbcP);
    this.add(btAddIno);
    
    gbcP.gridx = 1; gbcP.gridy = 2; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE;gbcP.weightx = 0; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(10, 10, 10, 20);
    btRepIno = new JButton("Replace in project");
    gbP.setConstraints(btRepIno, gbcP);
    this.add(btRepIno);

    gbcP.gridx = 0; gbcP.gridy = 3; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 0; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(10, 10, 0, 0);
    JLabel lbB = new JLabel("Boards in project:");
    gbP.setConstraints(lbB, gbcP);
    this.add(lbB);

    gbcP.gridx = 0; gbcP.gridy = 4; gbcP.gridwidth = 2; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.BOTH; gbcP.weightx = 1; gbcP.weighty = 5;
    gbcP.anchor = GridBagConstraints.CENTER;
    gbcP.insets = new Insets(0, 10, 0, 10);
    dataInIno = new ListData();
    lsInIno = new JList<String>(dataInIno);
    lsInIno.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scpInIno = new JScrollPane(lsInIno);
    gbP.setConstraints(scpInIno, gbcP);
    this.add(scpInIno);
    
    gbcP.gridx = 0; gbcP.gridy = 5; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 0; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(10, 10, 0, 0);
    JLabel lbC = new JLabel("Raw settings (for arduino-cli):");
    gbP.setConstraints(lbC, gbcP);
    this.add(lbC);

    gbcP.gridx = 0; gbcP.gridy = 6; gbcP.gridwidth = 2; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.BOTH; gbcP.weightx = 1; gbcP.weighty = 1;
    gbcP.anchor = GridBagConstraints.CENTER;
    gbcP.insets = new Insets(0, 10, 0, 10);
    taRaw = new JTextArea(strRaw);
    taRaw.setRows(3);
    JScrollPane scpTaRaw = new JScrollPane(taRaw);
    gbP.setConstraints(scpTaRaw, gbcP);this.add(scpTaRaw);
    
    gbcP.gridx = 0; gbcP.gridy = 7; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 1; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHWEST;
    gbcP.insets = new Insets(10, 20, 20, 10);
    btActivate = new JButton("Activate");
    gbP.setConstraints(btActivate, gbcP);
    this.add(btActivate);
    
    gbcP.gridx = 1; gbcP.gridy = 7; gbcP.gridwidth = 1; gbcP.gridheight = 1;
    gbcP.fill = GridBagConstraints.NONE; gbcP.weightx = 1; gbcP.weighty = 0;
    gbcP.anchor = GridBagConstraints.NORTHEAST;
    gbcP.insets = new Insets(10, 10, 20, 20);
    btClose = new JButton("Close");
    gbP.setConstraints(btClose, gbcP);
    this.add(btClose);
    
    taActual.setEditable(false);

    btAddIno.setEnabled(false);
    btRepIno.setEnabled(false);
    taRaw.setEditable(false);

    btActivate.addActionListener(new ActionListener() 
      {public void actionPerformed(ActionEvent e) { 
        if (IniIno.activateBoard(taRaw.getText())) {
          JOptionPane.showMessageDialog(btActivate, "Activated successfully");
          IniIno.dialogClose();
        } else
          JOptionPane.showMessageDialog(btActivate, "Error activate");
      } });
    btActivate.setEnabled(false);
    
    btAddIno.addActionListener(new ActionListener() 
      {public void actionPerformed(ActionEvent e) {
        if (!actSet.equals("") && foundId < 0) {
          IniIno.doInoParse(actLbl,actSet);
          addInoCfg(actSet,actLbl,true);
        }
        btAddIno.setEnabled(false);
      } });

    btRepIno.addActionListener(new ActionListener() 
      {public void actionPerformed(ActionEvent e) {
        if (!actSet.equals("") && foundId < 0 && selId >= 0) {
          BoardSet x = dataInIno.get(selId);
          String newname = x.name;
          String[] a = actSet.split(":",4),
                   b = x.cfg.split(":",4);
          if (a.length < 3 || b.length < 3 
              || !(a[0].equals(b[0]) && a[1].equals(b[1]) && a[2].equals(b[2])))
            newname = actLbl;
          IniIno.doInoParse(newname,actSet,x.name);
          foundId = selId;
          dataInIno.replace(selId,newname,actSet);
          taRaw.setText(actSet);
          btAddIno.setEnabled(false);
        }
        btRepIno.setEnabled(false);
      } });

    btClose.addActionListener(new ActionListener() 
      {public void actionPerformed(ActionEvent e) { 
        IniIno.dialogClose();
      } });
    lsInIno.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lsInIno.addListSelectionListener(new ListSelectionListener()
      {public void valueChanged(ListSelectionEvent e) { IniIno.panel.selectIniIno(e); } });
  }

  public void setActual(String as, String lbl) {
    actSet = as;
    actLbl = lbl;
    foundId = -1;
    taActual.setText(lbl + "\n" + as);
    taActual.setOpaque(true);
    btAddIno.setEnabled(true);
  }

  public void selectIniIno(ListSelectionEvent e) {
    taRaw.setText("");
    selId = -1;
    try { 
      selId = lsInIno.getSelectedIndex();
      if (selId >= 0 && selId < dataInIno.getSize()) {
        BoardSet x = dataInIno.get(selId);
        taRaw.setText(x.cfg);
        btActivate.setEnabled(x.installed && foundId != selId);
        btAddIno.setEnabled(foundId < 0);
        btRepIno.setEnabled(foundId < 0);
      }
    } catch (Exception ex) {}
  }
  
  public void removeInoCfgs() {
    dataInIno.clear();
    foundId = selId = -1;
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
      }
      taRaw.setText(cfg);
    }
  }
}
