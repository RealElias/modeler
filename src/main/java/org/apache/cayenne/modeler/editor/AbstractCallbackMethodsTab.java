/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/
package org.apache.cayenne.modeler.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.cayenne.map.CallbackDescriptor;
import org.apache.cayenne.map.CallbackMap;
import org.apache.cayenne.map.LifecycleEvent;
import org.apache.cayenne.map.ObjRelationship;
import org.apache.cayenne.modeler.Application;
import org.apache.cayenne.modeler.ProjectController;
import org.apache.cayenne.modeler.action.AbstractRemoveCallbackMethodAction;
import org.apache.cayenne.modeler.action.CreateCallbackMethodAction;
import org.apache.cayenne.modeler.action.RemoveCallbackMethodAction;
import org.apache.cayenne.modeler.event.CallbackMethodEvent;
import org.apache.cayenne.modeler.event.CallbackMethodListener;
import org.apache.cayenne.modeler.event.TablePopupHandler;
import org.apache.cayenne.modeler.pref.TableColumnPreferences;
import org.apache.cayenne.modeler.util.CayenneAction;
import org.apache.cayenne.modeler.util.CayenneTable;
import org.apache.cayenne.modeler.util.PanelFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

/**
 * Base abstract class for all calback methids editing tabs Contains logic for callback
 * methods displaying, creating, removing, esiting, reordering
 * 
 */
public abstract class AbstractCallbackMethodsTab extends JPanel {

    private static Log logger = LogFactory.getLog(AbstractCallbackMethodsTab.class);

    /**
     * mediator instance
     */
    ProjectController mediator;

    /**
     * toolbar for actions
     */
    protected JToolBar toolBar;
    
    /**
     * panel for displaying callback method tables
     */
    protected JPanel auxPanel;

    /**
     * preferences for the callback methods table
     */
    protected TableColumnPreferences tablePreferences;

    /**
     * Dropdown for callback type selection. Contains fixed list of 7 callback types.
     */
    protected JComboBox callbackTypeCombo = Application
            .getWidgetFactory()
            .createComboBox(
                    new Object[] {
                            new CallbackType(LifecycleEvent.POST_ADD, "post-add"),
                            new CallbackType(LifecycleEvent.PRE_PERSIST, "pre-persist"),
                            new CallbackType(LifecycleEvent.POST_PERSIST, "post-persist"),
                            new CallbackType(LifecycleEvent.PRE_UPDATE, "pre-update"),
                            new CallbackType(LifecycleEvent.POST_UPDATE, "post-update"),
                            new CallbackType(LifecycleEvent.PRE_REMOVE, "pre-remove"),
                            new CallbackType(LifecycleEvent.POST_REMOVE, "post-remove"),
                            new CallbackType(LifecycleEvent.POST_LOAD, "post-load"),
                    },
                    false);

    /**
     * constructor
     * 
     * @param mediator mediator instance
     */
    public AbstractCallbackMethodsTab(ProjectController mediator) {
        this.mediator = mediator;
        init();
        initController();
    }

    /**
     * @return CallbackMap with callback methods
     */
    protected abstract CallbackMap getCallbackMap();

    /**
     * creates filter pane for filtering callback methods list adds callback method type
     * dropdown
     * 
     * @param builder forms builder
     */
    protected void buildFilter(DefaultFormBuilder builder) {
        JLabel callbacktypeLabel = new JLabel("Callback type:");
        builder.append(callbacktypeLabel, callbackTypeCombo);
    }

    /**
     * @return create callback method action
     */
    protected CayenneAction getCreateCallbackMethodAction() {
        Application app = Application.getInstance();
        return app.getActionManager().getAction(CreateCallbackMethodAction.class);
    }

    /**
     * @return remove callback method action
     */
    protected AbstractRemoveCallbackMethodAction getRemoveCallbackMethodAction() {
        Application app = Application.getInstance();
        return app.getActionManager().getAction(RemoveCallbackMethodAction.class);
    }

    /**
     * GUI components initialization
     */
    protected void init() {
        this.setLayout(new BorderLayout());

        toolBar = new JToolBar();
        toolBar.add(getCreateCallbackMethodAction().buildButton());
        toolBar.add(getRemoveCallbackMethodAction().buildButton());

        FormLayout formLayout = new FormLayout("right:70dlu, 3dlu, fill:150dlu");
        DefaultFormBuilder builder = new DefaultFormBuilder(formLayout);
        buildFilter(builder);
        toolBar.add(builder.getPanel(), BorderLayout.NORTH);
        
        add(toolBar, BorderLayout.NORTH);

        auxPanel = new JPanel();
        auxPanel.setOpaque(false);
        auxPanel.setLayout(new BorderLayout());

        initTablePreferences();

        add(new JScrollPane(auxPanel), BorderLayout.CENTER);
    }

    /**
     * Inits the {@link TableColumnPreferences} object according to callback table name.
     */
    protected abstract void initTablePreferences();

    /**
     * listeners initialization
     */
    protected void initController() {
        mediator.addCallbackMethodListener(new CallbackMethodListener() {

            public void callbackMethodChanged(CallbackMethodEvent e) {
                rebuildTable();
            }

            public void callbackMethodAdded(CallbackMethodEvent e) {
                updateCallbackTypeCounters();
                rebuildTable();
            }

            public void callbackMethodRemoved(CallbackMethodEvent e) {
                updateCallbackTypeCounters();
                rebuildTable();
            }
        });
        
        callbackTypeCombo.addItemListener(new ItemListener() {

            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    mediator.setCurrentCallbackType((CallbackType) callbackTypeCombo
                            .getSelectedItem());
                    updateCallbackTypeCounters();
                    rebuildTable();
                }
            }
        });
    }

    protected void updateCallbackTypeCounters() {
        CallbackMap map = getCallbackMap();

        for (int i = 0; i < callbackTypeCombo.getItemCount(); i++) {
            CallbackType type = (CallbackType) callbackTypeCombo.getItemAt(i);

            if (map == null) {
                type.setCounter(0);
            }
            else {
                CallbackDescriptor callbackDescriptor = map.getCallbackDescriptor(type
                        .getType());
                type.setCounter(callbackDescriptor.getCallbackMethods().size());
            }
        }
        callbackTypeCombo.repaint();
    }

    /**
     * rebuilds table content
     */
    protected void rebuildTable() {
    	FormLayout formLayout = new FormLayout("left:" + auxPanel.getWidth() + "px");
        DefaultFormBuilder builder = new DefaultFormBuilder(formLayout);

    	auxPanel.removeAll();

    	CallbackMap callbackMap = getCallbackMap();
        
        if (callbackMap != null) {
        	for(int i = 0; i < callbackTypeCombo.getItemCount(); i++) {
        		builder.append(CreateTable((CallbackType)callbackTypeCombo.getItemAt(i)));
            }
        }

        auxPanel.add(builder.getPanel(), BorderLayout.CENTER);
        validate();
    }

    private JPanel CreateTable(CallbackType callbackType)
    {
   	
    	final CayenneTable cayenneTable = new CayenneTable();

        // drag-and-drop initialization
    	cayenneTable.setDragEnabled(true);
    	
        List methods = new ArrayList();
        CallbackDescriptor descriptor = null;
        CallbackMap callbackMap = getCallbackMap();

        descriptor = callbackMap.getCallbackDescriptor(callbackType.getType());
        for (String callbackMethod : descriptor.getCallbackMethods()) {
            methods.add(callbackMethod);
        }

        final CallbackDescriptorTableModel model = new CallbackDescriptorTableModel(
                mediator,
                this,
                methods,
                descriptor);

        cayenneTable.setModel(model);
        cayenneTable.setRowHeight(25);
        cayenneTable.setRowMargin(3);
        cayenneTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        cayenneTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        cayenneTable.setTransferHandler(new TransferHandler() {

            @Override
            protected Transferable createTransferable(JComponent c) {
                int rowIndex = cayenneTable.getSelectedRow();

                String result = null;
                if (rowIndex >= 0 && rowIndex < cayenneTable.getModel().getRowCount()) {
                    result = String.valueOf(cayenneTable.getModel().getValueAt(
                            rowIndex,
                            CallbackDescriptorTableModel.METHOD_NAME));
                }

                return new StringSelection(result);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY_OR_MOVE;
            }

            @Override
            public boolean importData(JComponent comp, Transferable t) {
                if (canImport(comp, t.getTransferDataFlavors())) {
                    String callbackMethod;
                    try {
                        callbackMethod = (String) t
                                .getTransferData(DataFlavor.stringFlavor);
                    }
                    catch (Exception e) {
                        logger.warn("Error transferring", e);
                        return false;
                    }

                    int rowIndex = cayenneTable.getSelectedRow();

                    CallbackDescriptor callbackDescriptor = ((CallbackDescriptorTableModel)cayenneTable.getCayenneModel()).getCallbackDescriptor();
                    mediator.setDirty(callbackDescriptor.moveMethod(
                            callbackMethod,
                            rowIndex));
                    rebuildTable();
                    return true;
                }

                return false;
            }

            @Override
            public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
                for (DataFlavor flavor : transferFlavors) {
                    if (DataFlavor.stringFlavor.equals(flavor)) {
                        return true;
                    }
                }
                return false;
            }
        });
        
        cayenneTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    String[] methods = new String[0];

                    if (cayenneTable.getSelectedRow() != -1) {
                        int[] sel = cayenneTable.getSelectedRows();
                        methods = new String[sel.length];

                        for (int i = 0; i < sel.length; i++) {
                            methods[i] = (String) cayenneTable
                                    .getValueAt(
                                            sel[i],
                                            cayenneTable
                                                    .convertColumnIndexToView(CallbackDescriptorTableModel.METHOD_NAME));
                        }
                    }

                    LifecycleEvent callbackType = ((CallbackDescriptorTableModel)cayenneTable.getCayenneModel()).getCallbackDescriptor().getCallbackType();
                    for(int i = 0; i < callbackTypeCombo.getItemCount(); i++) {
                    	if(callbackType == ((CallbackType)callbackTypeCombo.getItemAt(i)).getType())
                    		mediator.setCurrentCallbackType((CallbackType)callbackTypeCombo.getItemAt(i));
                    }
                    
                    mediator.setCurrentCallbackMethods(methods);
                    getRemoveCallbackMethodAction().setEnabled(methods.length > 0);
                    getRemoveCallbackMethodAction().setName(
                            getRemoveCallbackMethodAction().getActionName(
                                    methods.length > 1));
                }
            }
        });
        
        
        tablePreferences.bind(cayenneTable, null, null, null);

        // Create and install a popup
        JPopupMenu popup = new JPopupMenu();
        popup.add(getRemoveCallbackMethodAction().buildMenu());

        TablePopupHandler.install(cayenneTable, popup);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(cayenneTable.getTableHeader(), BorderLayout.NORTH);
        panel.add(cayenneTable, BorderLayout.CENTER);
        
        return panel;
    }

    class MyItemListener implements ItemListener  
    {  
      public void itemStateChanged(ItemEvent e) {  
        Object source = e.getSource();  
        if (source instanceof AbstractButton == false) return;  
        /*boolean checked = e.getStateChange() == ItemEvent.SELECTED;  
        for(int x = 0, y = table.getRowCount(); x < y; x++)  
        {  
          table.setValueAt(new Boolean(checked),x,0);  
        } */ 
      }  
    } 

    protected final CallbackType getSelectedCallbackType() {
    	return mediator.getCurrentCallbackType();
    }
}
