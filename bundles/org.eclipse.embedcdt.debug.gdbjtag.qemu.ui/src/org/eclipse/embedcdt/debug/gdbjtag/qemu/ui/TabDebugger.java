/*******************************************************************************
 * Copyright (c) 2007, 2012 QNX Software Systems and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *     Andy Jin - Hardware debugging UI improvements, bug 229946
 *     Anna Dushistova (MontaVista) - bug 241279
 *              - Hardware Debugging: Host name or ip address not saving in
 *                the debug configuration
 *     Andy Jin (QNX) - Added DSF debugging, bug 248593
 *     Bruce Griffith, Sage Electronic Engineering, LLC - bug 305943
 *              - API generalization to become transport-independent (e.g. to
 *                allow connections via serial ports and pipes).
 *     Liviu Ionescu - Arm version
 *     Jonah Graham - fix for Neon
 *     Liviu Ionescu - UI part extraction.
 ******************************************************************************/

package org.eclipse.embedcdt.debug.gdbjtag.qemu.ui;

import java.io.File;

import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.debug.gdbjtag.core.IGDBJtagConstants;
import org.eclipse.cdt.debug.gdbjtag.ui.GDBJtagImages;
import org.eclipse.cdt.dsf.gdb.IGDBLaunchConfigurationConstants;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.embedcdt.core.EclipseUtils;
import org.eclipse.embedcdt.debug.gdbjtag.core.data.CProjectAttributes;
import org.eclipse.embedcdt.debug.gdbjtag.qemu.core.Configuration;
import org.eclipse.embedcdt.debug.gdbjtag.qemu.core.ConfigurationAttributes;
import org.eclipse.embedcdt.debug.gdbjtag.qemu.core.preferences.DefaultPreferences;
import org.eclipse.embedcdt.debug.gdbjtag.qemu.core.preferences.PersistentPreferences;
import org.eclipse.embedcdt.internal.debug.gdbjtag.qemu.ui.Activator;
import org.eclipse.embedcdt.internal.debug.gdbjtag.qemu.ui.Messages;
import org.eclipse.embedcdt.internal.debug.gdbjtag.qemu.ui.preferences.GlobalMcuPage;
import org.eclipse.embedcdt.internal.debug.gdbjtag.qemu.ui.preferences.WorkspaceMcuPage;
import org.eclipse.embedcdt.internal.debug.gdbjtag.qemu.ui.properties.ProjectMcuPage;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;

/**
 * @since 7.0
 */
public class TabDebugger extends AbstractLaunchConfigurationTab {

	// ------------------------------------------------------------------------

	private static final String TAB_NAME = "Debugger";
	private static final String TAB_ID = Activator.PLUGIN_ID + ".ui.debuggertab";

	// ------------------------------------------------------------------------

	private ILaunchConfiguration fConfiguration;

	private Text fGdbClientPathLabel;
	private Text fGdbClientExecutable;
	private Text fGdbClientOtherOptions;
	private Text fGdbClientOtherCommands;

	private Button fDoStartGdbServer;
	private Text fGdbServerPathLabel;
	private Link fLink;

	// private Button fEnableSemihosting;
	// private Text fSemihostingCmdline;

	private Button fDisableGraphics;

	private Text fTargetIpAddress;
	private Text fTargetPortNumber;

	private Text fQemuBoardName;
	private Text fQemuDeviceName;
	// private Button fIsQemuVerbose;

	private Text fGdbServerGdbPort;

	private Text fGdbServerExecutable;
	private Button fGdbServerBrowseButton;
	private Button fGdbServerVariablesButton;

	private Text fGdbServerOtherOptions;

	private Button fDoGdbServerAllocateConsole;

	protected Button fUpdateThreadlistOnSuspend;

	protected String fSavedCmsisDeviceName;
	protected String fSavedCmsisBoardName;

	protected String fProjectName;

	private DefaultPreferences fDefaultPreferences;
	private PersistentPreferences fPersistentPreferences;

	// ------------------------------------------------------------------------

	protected TabDebugger(TabStartup tabStartup) {
		super();

		fSavedCmsisDeviceName = null;
		fSavedCmsisBoardName = null;

		fDefaultPreferences = Activator.getInstance().getDefaultPreferences();
		fPersistentPreferences = Activator.getInstance().getPersistentPreferences();
	}

	@Override
	public String getName() {
		return TAB_NAME;
	}

	@Override
	public Image getImage() {
		return GDBJtagImages.getDebuggerTabImage();
	}

	@Override
	public void createControl(Composite parent) {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.createControl() ");
		}

		Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		GridLayout layout = new GridLayout();
		comp.setLayout(layout);

		createGdbServerGroup(comp);

		createGdbClientControls(comp);

		createRemoteControl(comp);

		fUpdateThreadlistOnSuspend = new Button(comp, SWT.CHECK);
		fUpdateThreadlistOnSuspend.setText(Messages.getString("DebuggerTab.update_thread_list_on_suspend_Text"));
		fUpdateThreadlistOnSuspend
				.setToolTipText(Messages.getString("DebuggerTab.update_thread_list_on_suspend_ToolTipText"));

		Link restoreDefaults;
		{
			restoreDefaults = new Link(comp, SWT.NONE);
			restoreDefaults.setText(Messages.getString("DebuggerTab.restoreDefaults_Link"));
			restoreDefaults.setToolTipText(Messages.getString("DebuggerTab.restoreDefaults_ToolTipText"));

			GridData gd = new GridData();
			gd.grabExcessHorizontalSpace = true;
			gd.horizontalAlignment = SWT.RIGHT;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns;
			restoreDefaults.setLayoutData(gd);
		}

		// --------------------------------------------------------------------

		fUpdateThreadlistOnSuspend.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateLaunchConfigurationDialog();
			}
		});

		restoreDefaults.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent event) {
				initializeFromDefaults();
				scheduleUpdateJob();
			}
		});
	}

	private void browseButtonSelected(String title, Text text) {
		FileDialog dialog = new FileDialog(getShell(), SWT.NONE);
		dialog.setText(title);
		String str = text.getText().trim();
		int lastSeparatorIndex = str.lastIndexOf(File.separator);
		if (lastSeparatorIndex != -1)
			dialog.setFilterPath(str.substring(0, lastSeparatorIndex));
		str = dialog.open();
		if (str != null)
			text.setText(str);
	}

	private void variablesButtonSelected(Text text) {
		StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(getShell());
		if (dialog.open() == StringVariableSelectionDialog.OK) {
			text.insert(dialog.getVariableExpression());
		}
	}

	@SuppressWarnings("unused")
	private void createOptionsControl(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		{
			group.setText(Messages.getString("DebuggerTab.interfaceGroup_Text"));
			GridLayout layout = new GridLayout();
			group.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			group.setLayoutData(gd);
		}

		Composite comp = new Composite(group, SWT.NONE);
		{
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginHeight = 0;
			comp.setLayout(layout);
		}
	}

	private void createGdbServerGroup(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		{
			GridLayout layout = new GridLayout();
			group.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			group.setLayoutData(gd);
			group.setText(Messages.getString("DebuggerTab.gdbServerGroup_Text"));
		}

		Composite comp = new Composite(group, SWT.NONE);
		{
			GridLayout layout = new GridLayout();
			layout.numColumns = 5;
			layout.marginHeight = 0;
			comp.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			comp.setLayoutData(gd);
		}

		{
			fDoStartGdbServer = new Button(comp, SWT.CHECK);
			fDoStartGdbServer.setText(Messages.getString("DebuggerTab.doStartGdbServer_Text"));
			fDoStartGdbServer.setToolTipText(Messages.getString("DebuggerTab.doStartGdbServer_ToolTipText"));
			GridData gd = new GridData();
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns;
			fDoStartGdbServer.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerExecutable_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbServerExecutable_ToolTipText"));

			Composite local = new Composite(comp, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			local.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			local.setLayoutData(gd);
			{
				fGdbServerExecutable = new Text(local, SWT.SINGLE | SWT.BORDER);
				gd = new GridData(GridData.FILL_HORIZONTAL);
				fGdbServerExecutable.setLayoutData(gd);

				fGdbServerBrowseButton = new Button(local, SWT.NONE);
				fGdbServerBrowseButton.setText(Messages.getString("DebuggerTab.gdbServerExecutableBrowse"));

				fGdbServerVariablesButton = new Button(local, SWT.NONE);
				fGdbServerVariablesButton.setText(Messages.getString("DebuggerTab.gdbServerExecutableVariable"));
			}
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerActualPath_Label"));

			fGdbServerPathLabel = new Text(comp, SWT.SINGLE | SWT.BORDER);
			GridData gd = new GridData(SWT.FILL, 0, true, false);
			gd.horizontalSpan = 4;
			fGdbServerPathLabel.setLayoutData(gd);

			fGdbServerPathLabel.setEnabled(true);
			fGdbServerPathLabel.setEditable(false);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText("");

			fLink = new Link(comp, SWT.NONE);
			fLink.setText(Messages.getString("DebuggerTab.gdbServerActualPath_link"));
			GridData gd = new GridData();
			gd.horizontalSpan = 4;
			fLink.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerBoard_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbServerBoard_ToolTipText"));

			fQemuBoardName = new Text(comp, SWT.SINGLE | SWT.BORDER);
			fQemuBoardName.setToolTipText(Messages.getString("DebuggerTab.gdbServerBoard_ToolTipText"));

			GridData gd = new GridData();
			gd.widthHint = 200;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fQemuBoardName.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerDevice_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbServerDevice_ToolTipText"));

			fQemuDeviceName = new Text(comp, SWT.SINGLE | SWT.BORDER);
			fQemuDeviceName.setToolTipText(Messages.getString("DebuggerTab.gdbServerDevice_ToolTipText"));

			GridData gd = new GridData();
			gd.widthHint = 200;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fQemuDeviceName.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerGdbPort_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbServerGdbPort_ToolTipText"));

			fGdbServerGdbPort = new Text(comp, SWT.SINGLE | SWT.BORDER);
			fGdbServerGdbPort.setToolTipText(Messages.getString("DebuggerTab.gdbServerGdbPort_ToolTipText"));

			GridData gd = new GridData();
			gd.widthHint = 60;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fGdbServerGdbPort.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbServerOther_Label")); //$NON-NLS-1$
			label.setToolTipText(Messages.getString("DebuggerTab.gdbServerOther_ToolTipText"));
			GridData gd = new GridData();
			gd.verticalAlignment = SWT.TOP;
			label.setLayoutData(gd);

			fGdbServerOtherOptions = new Text(comp, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
			fGdbServerOtherOptions.setToolTipText(Messages.getString("DebuggerTab.gdbServerOther_ToolTipText"));

			gd = new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.heightHint = 60;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fGdbServerOtherOptions.setLayoutData(gd);
		}

		{
			Composite local = new Composite(comp, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.makeColumnsEqualWidth = true;
			local.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns;
			local.setLayoutData(gd);

			/*
			 * fEnableSemihosting = new Button(local, SWT.CHECK);
			 * fEnableSemihosting.setText(Messages.getString(
			 * "DebuggerTab.enableSemihosting_Text"));
			 * fEnableSemihosting.setToolTipText(Messages.getString(
			 * "DebuggerTab.enableSemihosting_ToolTipText"));
			 *
			 * gd = new GridData(GridData.FILL_HORIZONTAL);
			 * fEnableSemihosting.setLayoutData(gd);
			 */

			fDisableGraphics = new Button(local, SWT.CHECK);
			fDisableGraphics.setText(Messages.getString("DebuggerTab.disableGraphics_Text"));
			fDisableGraphics.setToolTipText(Messages.getString("DebuggerTab.disableGraphics_ToolTipText"));

			gd = new GridData(GridData.FILL_HORIZONTAL);
			fDisableGraphics.setLayoutData(gd);
		}

		/*
		 * { Label label = new Label(comp, SWT.NONE);
		 * label.setText(Messages.getString("DebuggerTab.gdbSemihostingCmdline_Label"));
		 * //$NON-NLS-1$ label.setToolTipText(Messages.getString(
		 * "DebuggerTab.gdbSemihostingCmdline_ToolTipText")); GridData gd = new
		 * GridData(); gd.verticalAlignment = SWT.TOP; label.setLayoutData(gd);
		 *
		 * fSemihostingCmdline = new Text(comp, SWT.SINGLE | SWT.BORDER);
		 * fSemihostingCmdline.setToolTipText(Messages.getString(
		 * "DebuggerTab.gdbSemihostingCmdline_ToolTipText"));
		 *
		 * gd = new GridData(SWT.FILL, SWT.FILL, true, true); gd.horizontalSpan =
		 * ((GridLayout) comp.getLayout()).numColumns - 1;
		 * fSemihostingCmdline.setLayoutData(gd); }
		 */

		{
			Composite local = new Composite(comp, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.makeColumnsEqualWidth = true;
			local.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns;
			local.setLayoutData(gd);

			fDoGdbServerAllocateConsole = new Button(local, SWT.CHECK);
			fDoGdbServerAllocateConsole.setText(Messages.getString("DebuggerTab.gdbServerAllocateConsole_Label"));
			fDoGdbServerAllocateConsole
					.setToolTipText(Messages.getString("DebuggerTab.gdbServerAllocateConsole_ToolTipText"));
			gd = new GridData(GridData.FILL_HORIZONTAL);
			fDoGdbServerAllocateConsole.setLayoutData(gd);
			/*
			 * fIsQemuVerbose = new Button(local, SWT.CHECK);
			 * fIsQemuVerbose.setText(Messages.getString(
			 * "DebuggerTab.gdbServerVerbose_Label"));
			 * fIsQemuVerbose.setToolTipText(Messages.getString(
			 * "DebuggerTab.gdbServerVerbose_ToolTipText"));
			 *
			 * gd = new GridData(GridData.FILL_HORIZONTAL);
			 * fIsQemuVerbose.setLayoutData(gd);
			 */
		}

		// ----- Actions ------------------------------------------------------

		ModifyListener scheduleUpdateJobModifyListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scheduleUpdateJob();
			}
		};

		SelectionAdapter scheduleUpdateJobSelectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				scheduleUpdateJob();
			}
		};

		fDoStartGdbServer.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doStartGdbServerChanged();

				if (fDoStartGdbServer.getSelection()) {
					fTargetIpAddress.setText(DefaultPreferences.REMOTE_IP_ADDRESS_LOCALHOST);
				}

				scheduleUpdateJob();
			}
		});

		/*
		 * fEnableSemihosting.addSelectionListener(new SelectionAdapter() {
		 *
		 * @Override public void widgetSelected(SelectionEvent e) {
		 * doEnableSemihostingChanged(); scheduleUpdateJob(); } });
		 */

		fDisableGraphics.addSelectionListener(scheduleUpdateJobSelectionAdapter);

		fGdbServerExecutable.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {

				scheduleUpdateJob(); // provides much better performance for
										// Text listeners
				updateGdbServerActualPath();
			}
		});

		fGdbServerBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseButtonSelected(Messages.getString("DebuggerTab.gdbServerExecutableBrowse_Title"),
						fGdbServerExecutable);
			}
		});

		fGdbServerVariablesButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				variablesButtonSelected(fGdbServerExecutable);
			}
		});

		fLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				String text = e.text;
				if (Activator.getInstance().isDebugging()) {
					System.out.println(text);
				}

				int ret = -1;
				if ("global".equals(text)) {
					ret = PreferencesUtil.createPreferenceDialogOn(parent.getShell(), GlobalMcuPage.ID, null, null)
							.open();
				} else if ("workspace".equals(text)) {
					ret = PreferencesUtil.createPreferenceDialogOn(parent.getShell(), WorkspaceMcuPage.ID, null, null)
							.open();
				} else if ("project".equals(text)) {
					assert (fConfiguration != null);
					IProject project = EclipseUtils.getProjectByLaunchConfiguration(fConfiguration);
					ret = PreferencesUtil
							.createPropertyDialogOn(parent.getShell(), project, ProjectMcuPage.ID, null, null, 0)
							.open();
				}

				if (ret == Window.OK) {
					updateGdbServerActualPath();
				}
			}
		});

		fQemuBoardName.addModifyListener(scheduleUpdateJobModifyListener);

		fQemuDeviceName.addModifyListener(scheduleUpdateJobModifyListener);

		fGdbServerGdbPort.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {

				// make the target port the same
				fTargetPortNumber.setText(fGdbServerGdbPort.getText());
				scheduleUpdateJob();
			}
		});

		fGdbServerOtherOptions.addModifyListener(scheduleUpdateJobModifyListener);

		// fSemihostingCmdline.addModifyListener(scheduleUpdateJobModifyListener);

		fDoGdbServerAllocateConsole.addSelectionListener(scheduleUpdateJobSelectionAdapter);

		// fIsQemuVerbose.addSelectionListener(scheduleUpdateJobSelectionAdapter);
	}

	private void createGdbClientControls(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		{
			group.setText(Messages.getString("DebuggerTab.gdbSetupGroup_Text"));
			GridLayout layout = new GridLayout();
			group.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			group.setLayoutData(gd);
		}

		Composite comp = new Composite(group, SWT.NONE);
		{
			GridLayout layout = new GridLayout();
			layout.numColumns = 5;
			layout.marginHeight = 0;
			comp.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			comp.setLayoutData(gd);
		}

		Button browseButton;
		Button variableButton;
		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbCommand_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbCommand_ToolTipText"));

			Composite local = new Composite(comp, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			local.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			local.setLayoutData(gd);
			{
				fGdbClientExecutable = new Text(local, SWT.SINGLE | SWT.BORDER);
				gd = new GridData(GridData.FILL_HORIZONTAL);
				fGdbClientExecutable.setLayoutData(gd);

				browseButton = new Button(local, SWT.NONE);
				browseButton.setText(Messages.getString("DebuggerTab.gdbCommandBrowse"));

				variableButton = new Button(local, SWT.NONE);
				variableButton.setText(Messages.getString("DebuggerTab.gdbCommandVariable"));
			}
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbCommandActualPath_Label"));

			fGdbClientPathLabel = new Text(comp, SWT.SINGLE | SWT.BORDER);
			GridData gd = new GridData(SWT.FILL, 0, true, false);
			gd.horizontalSpan = 4;
			fGdbClientPathLabel.setLayoutData(gd);

			fGdbClientPathLabel.setEnabled(true);
			fGdbClientPathLabel.setEditable(false);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbOtherOptions_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbOtherOptions_ToolTipText"));
			GridData gd = new GridData();
			label.setLayoutData(gd);

			fGdbClientOtherOptions = new Text(comp, SWT.SINGLE | SWT.BORDER);
			gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fGdbClientOtherOptions.setLayoutData(gd);
		}

		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.gdbOtherCommands_Label"));
			label.setToolTipText(Messages.getString("DebuggerTab.gdbOtherCommands_ToolTipText"));
			GridData gd = new GridData();
			gd.verticalAlignment = SWT.TOP;
			label.setLayoutData(gd);

			fGdbClientOtherCommands = new Text(comp, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
			gd = new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.heightHint = 60;
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			fGdbClientOtherCommands.setLayoutData(gd);
		}

		// ----- Actions ------------------------------------------------------

		fGdbClientExecutable.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {

				scheduleUpdateJob(); // provides much better performance for
										// Text listeners
				updateGdbClientActualPath();
			}
		});

		fGdbClientOtherOptions.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scheduleUpdateJob();
			}
		});

		fGdbClientOtherCommands.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scheduleUpdateJob();
			}
		});

		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseButtonSelected(Messages.getString("DebuggerTab.gdbCommandBrowse_Title"), fGdbClientExecutable);
			}
		});

		variableButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				variablesButtonSelected(fGdbClientExecutable);
			}
		});
	}

	private void createRemoteControl(Composite parent) {

		Group group = new Group(parent, SWT.NONE);
		{
			group.setText(Messages.getString("DebuggerTab.remoteGroup_Text"));
			GridLayout layout = new GridLayout();
			group.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			group.setLayoutData(gd);
		}

		Composite comp = new Composite(group, SWT.NONE);
		{
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			comp.setLayout(layout);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			comp.setLayoutData(gd);
		}

		// Create entry fields for TCP/IP connections
		{
			Label label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.ipAddressLabel")); //$NON-NLS-1$

			fTargetIpAddress = new Text(comp, SWT.BORDER);
			GridData gd = new GridData();
			gd.widthHint = 125;
			fTargetIpAddress.setLayoutData(gd);

			label = new Label(comp, SWT.NONE);
			label.setText(Messages.getString("DebuggerTab.portNumberLabel")); //$NON-NLS-1$

			fTargetPortNumber = new Text(comp, SWT.BORDER);
			gd = new GridData();
			gd.widthHint = 125;
			fTargetPortNumber.setLayoutData(gd);
		}

		// ---- Actions -------------------------------------------------------

		// Add watchers for user data entry
		fTargetIpAddress.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scheduleUpdateJob(); // provides much better performance for
										// Text listeners
			}
		});
		fTargetPortNumber.addVerifyListener(new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				e.doit = Character.isDigit(e.character) || Character.isISOControl(e.character);
			}
		});
		fTargetPortNumber.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scheduleUpdateJob(); // provides much better performance for
										// Text listeners
			}
		});

	}

	private void updateGdbServerActualPath() {

		assert (fConfiguration != null);
		String fullCommand = Configuration.getGdbServerCommand(fConfiguration, fGdbServerExecutable.getText());
		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.updateActualpath() \"" + fullCommand + "\"");
		}
		fGdbServerPathLabel.setText(fullCommand);
	}

	private void updateGdbClientActualPath() {

		assert (fConfiguration != null);
		String fullCommand = Configuration.getGdbClientCommand(fConfiguration, fGdbClientExecutable.getText());
		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.updateGdbClientActualPath() \"" + fullCommand + "\"");
		}
		fGdbClientPathLabel.setText(fullCommand);
	}

	private void doStartGdbServerChanged() {

		boolean enabled = fDoStartGdbServer.getSelection();

		fGdbServerExecutable.setEnabled(enabled);
		fGdbServerBrowseButton.setEnabled(enabled);
		fGdbServerVariablesButton.setEnabled(enabled);
		fGdbServerOtherOptions.setEnabled(enabled);

		fGdbServerGdbPort.setEnabled(enabled);

		// fEnableSemihosting.setEnabled(enabled);
		// fSemihostingCmdline.setEnabled(enabled && fEnableSemihosting.getSelection());
		fDoGdbServerAllocateConsole.setEnabled(enabled);
		fDisableGraphics.setEnabled(enabled);

		fQemuBoardName.setEnabled(enabled);
		// fIsQemuVerbose.setEnabled(enabled);

		// Disable remote target params when the server is started
		fTargetIpAddress.setEnabled(!enabled);
		fTargetPortNumber.setEnabled(!enabled);

		fGdbServerPathLabel.setEnabled(enabled);
		fLink.setEnabled(enabled);
	}

	/*
	 * private void doEnableSemihostingChanged() { boolean enabled =
	 * fEnableSemihosting.getSelection();
	 *
	 * fSemihostingCmdline.setEnabled(enabled); }
	 */

	/**
	 * Get the project name associated to this launch configuration.
	 *
	 * @param configuration
	 * @return a String with the project name, or empty.
	 */
	private String getProjectName(ILaunchConfiguration configuration) {

		if (configuration != null) {
			// Get project name from launch configuration.
			try {
				String str = configuration.getAttribute(ICDTLaunchConfigurationConstants.ATTR_PROJECT_NAME,
						(String) null);
				fProjectName = str.replace(' ', '_');
			} catch (CoreException e) {

			}
		}

		if (fProjectName == null) {
			fProjectName = "";
		}

		return fProjectName;
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.initializeFrom() " + configuration.getName());
		}

		fConfiguration = configuration;

		try {
			Boolean booleanDefault;
			String stringDefault;

			// QEMU GDB server
			{
				// Start server locally
				booleanDefault = fPersistentPreferences.getGdbServerDoStart();
				fDoStartGdbServer.setSelection(
						configuration.getAttribute(ConfigurationAttributes.DO_START_GDB_SERVER, booleanDefault));

				// Executable
				stringDefault = fPersistentPreferences.getGdbServerExecutable();
				fGdbServerExecutable.setText(
						configuration.getAttribute(ConfigurationAttributes.GDB_SERVER_EXECUTABLE, stringDefault));

				String boardName = CProjectAttributes.getCmsisBoardName(configuration);
				fSavedCmsisBoardName = boardName;
				String deviceName = CProjectAttributes.getCmsisDeviceName(configuration);
				fSavedCmsisDeviceName = deviceName;

				// If the project has assigned either a board name,
				// or a device name, use them.
				if (((boardName == null || boardName.isEmpty()) && (deviceName == null || deviceName.isEmpty()))) {

					// Otherwise try the names used previously
					boardName = fPersistentPreferences.getQemuBoardName();
					deviceName = fPersistentPreferences.getQemuDeviceName();
				}

				fQemuBoardName.setText(configuration.getAttribute(ConfigurationAttributes.GDB_SERVER_BOARD_NAME,
						boardName == null ? "" : boardName));
				fQemuDeviceName.setText(configuration.getAttribute(ConfigurationAttributes.GDB_SERVER_DEVICE_NAME,
						deviceName == null ? "" : deviceName));

				// Ports
				fGdbServerGdbPort.setText(
						Integer.toString(configuration.getAttribute(ConfigurationAttributes.GDB_SERVER_GDB_PORT_NUMBER,
								DefaultPreferences.SERVER_GDB_PORT_NUMBER_DEFAULT)));

				// Other options
				stringDefault = fPersistentPreferences.getGdbServerOtherOptions();
				fGdbServerOtherOptions
						.setText(configuration.getAttribute(ConfigurationAttributes.GDB_SERVER_OTHER, stringDefault));

				// Enable semihosting
				/*
				 * booleanDefault = fPersistentPreferences.getQemuEnableSemihosting();
				 * fEnableSemihosting.setSelection(
				 * configuration.getAttribute(ConfigurationAttributes.ENABLE_SEMIHOSTING,
				 * booleanDefault));
				 */

				/*
				 * stringDefault = getProjectName(configuration); fSemihostingCmdline.setText(
				 * configuration.getAttribute(ConfigurationAttributes.SEMIHOSTING_CMDLINE,
				 * stringDefault));
				 */

				// Disable graphics
				booleanDefault = fPersistentPreferences.getQemuDisableGraphics();
				fDisableGraphics.setSelection(
						configuration.getAttribute(ConfigurationAttributes.DISABLE_GRAPHICS, booleanDefault));

				// Allocate server console
				if (EclipseUtils.isWindows()) {
					fDoGdbServerAllocateConsole.setSelection(true);
				} else {
					fDoGdbServerAllocateConsole.setSelection(
							configuration.getAttribute(ConfigurationAttributes.DO_GDB_SERVER_ALLOCATE_CONSOLE,
									DefaultPreferences.DO_GDB_SERVER_ALLOCATE_CONSOLE_DEFAULT));
				}

				/*
				 * booleanDefault = fPersistentPreferences.getQemuIsVerbose();
				 * fIsQemuVerbose.setSelection(
				 * configuration.getAttribute(ConfigurationAttributes.IS_GDB_SERVER_VERBOSE,
				 * booleanDefault));
				 */

			}

			// GDB Client Setup
			{
				// Executable
				stringDefault = fPersistentPreferences.getGdbClientExecutable();
				String gdbCommandAttr = configuration.getAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME,
						stringDefault);
				fGdbClientExecutable.setText(gdbCommandAttr);

				// Other options
				stringDefault = fPersistentPreferences.getGdbClientOtherOptions();
				fGdbClientOtherOptions.setText(
						configuration.getAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_OPTIONS, stringDefault));

				stringDefault = fPersistentPreferences.getGdbClientCommands();
				fGdbClientOtherCommands.setText(
						configuration.getAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_COMMANDS, stringDefault));
			}

			// Remote target
			{
				fTargetIpAddress.setText(configuration.getAttribute(IGDBJtagConstants.ATTR_IP_ADDRESS,
						DefaultPreferences.REMOTE_IP_ADDRESS_DEFAULT)); // $NON-NLS-1$

				int storedPort = 0;
				storedPort = configuration.getAttribute(IGDBJtagConstants.ATTR_PORT_NUMBER, 0); // Default
																								// 0

				// 0 means undefined, use default
				if ((storedPort <= 0) || (65535 < storedPort)) {
					storedPort = DefaultPreferences.REMOTE_PORT_NUMBER_DEFAULT;
				}

				String portString = Integer.toString(storedPort); // $NON-NLS-1$
				fTargetPortNumber.setText(portString);

			}

			doStartGdbServerChanged();

			// Force thread update
			boolean updateThreadsOnSuspend = configuration.getAttribute(
					IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_UPDATE_THREADLIST_ON_SUSPEND,
					ConfigurationAttributes.UPDATE_THREAD_LIST_DEFAULT);
			fUpdateThreadlistOnSuspend.setSelection(updateThreadsOnSuspend);

		} catch (CoreException e) {
			Activator.log(e.getStatus());
		}

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.initializeFrom() completed " + configuration.getName());
		}
	}

	public void initializeFromDefaults() {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.initializeFromDefaults()");
		}

		String stringDefault;
		boolean booleanDefault;

		// QEMU GDB server
		{
			// Start server locally
			booleanDefault = fDefaultPreferences.getGdbServerDoStart();
			fDoStartGdbServer.setSelection(booleanDefault);

			// Executable
			stringDefault = fDefaultPreferences.getGdbServerExecutable();
			fGdbServerExecutable.setText(stringDefault);

			String boardName;
			String deviceName;
			if (fSavedCmsisBoardName != null || fSavedCmsisDeviceName != null) {
				// If the project has assigned either a board name,
				// or a device name, use them.
				boardName = fSavedCmsisBoardName != null ? fSavedCmsisBoardName : "";
				deviceName = fSavedCmsisDeviceName != null ? fSavedCmsisDeviceName : "";
			} else {
				boardName = DefaultPreferences.QEMU_BOARD_NAME_DEFAULT;
				deviceName = DefaultPreferences.QEMU_DEVICE_NAME_DEFAULT;
			}

			fQemuBoardName.setText(boardName);
			fQemuDeviceName.setText(deviceName);

			// Ports
			fGdbServerGdbPort.setText(Integer.toString(DefaultPreferences.SERVER_GDB_PORT_NUMBER_DEFAULT));

			// Other options
			stringDefault = fDefaultPreferences.getGdbServerOtherOptions();
			fGdbServerOtherOptions.setText(stringDefault);

			// Enable semihosting
			/*
			 * booleanDefault = fDefaultPreferences.getQemuEnableSemihosting();
			 * fEnableSemihosting.setSelection(booleanDefault);
			 *
			 * fSemihostingCmdline.setText(getProjectName(null));
			 */

			// Disable graphics
			booleanDefault = fDefaultPreferences.getQemuDisableGraphics();
			fDisableGraphics.setSelection(booleanDefault);

			// Allocate server console
			if (EclipseUtils.isWindows()) {
				fDoGdbServerAllocateConsole.setSelection(true);
			} else {
				fDoGdbServerAllocateConsole.setSelection(DefaultPreferences.DO_GDB_SERVER_ALLOCATE_CONSOLE_DEFAULT);
			}

			// fIsQemuVerbose.setSelection(DefaultPreferences.QEMU_IS_VERBOSE_DEFAULT);
		}

		// GDB Client Setup
		{
			stringDefault = fDefaultPreferences.getGdbClientExecutable();
			// Executable
			fGdbClientExecutable.setText(stringDefault);

			// Other options
			stringDefault = fDefaultPreferences.getGdbClientOtherOptions();
			fGdbClientOtherOptions.setText(stringDefault);

			stringDefault = fDefaultPreferences.getGdbClientCommands();
			fGdbClientOtherCommands.setText(stringDefault);
		}

		// Remote target
		{
			fTargetIpAddress.setText(DefaultPreferences.REMOTE_IP_ADDRESS_DEFAULT); // $NON-NLS-1$

			String portString = Integer.toString(DefaultPreferences.REMOTE_PORT_NUMBER_DEFAULT); // $NON-NLS-1$
			fTargetPortNumber.setText(portString);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.debug.ui.AbstractLaunchConfigurationTab#getId()
	 */
	@Override
	public String getId() {
		return TAB_ID;
	}

	@Override
	public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.activated() " + workingCopy.getName());
		}
	}

	@Override
	public void deactivated(ILaunchConfigurationWorkingCopy workingCopy) {
		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.deactivated() " + workingCopy.getName());
		}
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.isValid() " + launchConfig.getName());
		}

		setErrorMessage(null);
		setMessage(null);

		boolean result = true;

		if (fDoStartGdbServer != null && fDoStartGdbServer.getSelection()) {

			if (fGdbServerExecutable != null && fGdbServerExecutable.getText().trim().isEmpty()) {
				setErrorMessage("GDB server executable path?");
				result = false;
			}

			if (fGdbServerGdbPort != null && fGdbServerGdbPort.getText().trim().isEmpty()) {
				setErrorMessage("GDB port?");
				result = false;
			}

			if (fQemuBoardName != null && fQemuBoardName.getText().trim().isEmpty()) {
				setErrorMessage("Board name?");
				result = false;
			}
		}

		if (fGdbClientExecutable != null && fGdbClientExecutable.getText().trim().isEmpty()) {
			setErrorMessage("GDB client executable name?");
			result = false;
		}

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.isValid() " + launchConfig.getName() + " = " + result);
		}

		return result;
	}

	@Override
	public boolean canSave() {
		if (fDoStartGdbServer != null && fDoStartGdbServer.getSelection()) {
			if (fGdbServerExecutable != null && fGdbServerExecutable.getText().trim().isEmpty())
				return false;

			if (fQemuBoardName != null && fQemuBoardName.getText().trim().isEmpty())
				return false;

			if (fGdbServerGdbPort != null && fGdbServerGdbPort.getText().trim().isEmpty())
				return false;
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.performApply() " + configuration.getName());
		}

		{
			// legacy definition; although the jtag device class is not used,
			// it must be there, to avoid NPEs
			configuration.setAttribute(ConfigurationAttributes.ATTR_JTAG_DEVICE, ConfigurationAttributes.JTAG_DEVICE);
		}

		boolean booleanValue;
		String stringValue;

		// QEMU server
		{
			// Start server
			booleanValue = fDoStartGdbServer.getSelection();
			configuration.setAttribute(ConfigurationAttributes.DO_START_GDB_SERVER, booleanValue);
			fPersistentPreferences.putGdbServerDoStart(booleanValue);

			// Executable
			stringValue = fGdbServerExecutable.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_EXECUTABLE, stringValue);
			fPersistentPreferences.putGdbServerExecutable(stringValue);

			// Ports
			int port;
			if (!fGdbServerGdbPort.getText().trim().isEmpty()) {
				port = Integer.parseInt(fGdbServerGdbPort.getText().trim());
				configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_GDB_PORT_NUMBER, port);
			} else {
				Activator.log("empty fGdbServerGdbPort");
			}

			// Board name
			stringValue = fQemuBoardName.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_BOARD_NAME, stringValue);
			fPersistentPreferences.putQemuBoardName(stringValue);

			// Device name
			stringValue = fQemuDeviceName.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_DEVICE_NAME, stringValue);
			fPersistentPreferences.putQemuDeviceName(stringValue);

			// Other options
			stringValue = fGdbServerOtherOptions.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_OTHER, stringValue);
			fPersistentPreferences.putGdbServerOtherOptions(stringValue);

			// Enable semihosting
			/*
			 * booleanValue = fEnableSemihosting.getSelection();
			 * configuration.setAttribute(ConfigurationAttributes.ENABLE_SEMIHOSTING,
			 * booleanValue); fPersistentPreferences.putQemuEnableSemihosting(booleanValue);
			 *
			 * // Semihosting command line stringValue =
			 * fSemihostingCmdline.getText().trim();
			 * configuration.setAttribute(ConfigurationAttributes.SEMIHOSTING_CMDLINE,
			 * stringValue);
			 */

			// Disable graphics
			booleanValue = fDisableGraphics.getSelection();
			configuration.setAttribute(ConfigurationAttributes.DISABLE_GRAPHICS, booleanValue);
			fPersistentPreferences.putQemuDisableGraphics(booleanValue);

			// Allocate server console
			configuration.setAttribute(ConfigurationAttributes.DO_GDB_SERVER_ALLOCATE_CONSOLE,
					fDoGdbServerAllocateConsole.getSelection());

			/*
			 * booleanValue = fIsQemuVerbose.getSelection();
			 * configuration.setAttribute(ConfigurationAttributes.IS_GDB_SERVER_VERBOSE,
			 * booleanValue); fPersistentPreferences.putQemuIsVerbose(booleanValue);
			 */
		}

		// GDB client
		{
			// always use remote
			configuration.setAttribute(IGDBJtagConstants.ATTR_USE_REMOTE_TARGET,
					DefaultPreferences.USE_REMOTE_TARGET_DEFAULT);

			stringValue = fGdbClientExecutable.getText().trim();
			// configuration.setAttribute(
			// IMILaunchConfigurationConstants.ATTR_DEBUG_NAME,
			// clientExecutable);
			configuration.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME, stringValue); // DSF
			fPersistentPreferences.putGdbClientExecutable(stringValue);

			stringValue = fGdbClientOtherOptions.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_OPTIONS, stringValue);
			fPersistentPreferences.putGdbClientOtherOptions(stringValue);

			stringValue = fGdbClientOtherCommands.getText().trim();
			configuration.setAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_COMMANDS, stringValue);
			fPersistentPreferences.putGdbClientCommands(stringValue);
		}

		{
			if (fDoStartGdbServer.getSelection()) {
				configuration.setAttribute(IGDBJtagConstants.ATTR_IP_ADDRESS, "localhost");

				String str = fGdbServerGdbPort.getText().trim();
				if (!str.isEmpty()) {
					try {
						int port;
						port = Integer.parseInt(str);
						configuration.setAttribute(IGDBJtagConstants.ATTR_PORT_NUMBER, port);
					} catch (NumberFormatException e) {
						Activator.log(e);
					}
				}
			} else {
				String ip = fTargetIpAddress.getText().trim();
				configuration.setAttribute(IGDBJtagConstants.ATTR_IP_ADDRESS, ip);

				String str = fTargetPortNumber.getText().trim();
				if (!str.isEmpty()) {
					try {
						int port = Integer.valueOf(str).intValue();
						configuration.setAttribute(IGDBJtagConstants.ATTR_PORT_NUMBER, port);
					} catch (NumberFormatException e) {
						Activator.log(e);
					}
				}
			}
		}

		// Force thread update
		configuration.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_UPDATE_THREADLIST_ON_SUSPEND,
				fUpdateThreadlistOnSuspend.getSelection());

		fPersistentPreferences.flush();

		if (Activator.getInstance().isDebugging()) {
			System.out.println(
					"qemu.TabDebugger.performApply() completed " + configuration.getName() + ", dirty=" + isDirty());
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("qemu.TabDebugger.setDefaults() " + configuration.getName());
		}

		boolean defaultBoolean;
		String defaultString;

		configuration.setAttribute(ConfigurationAttributes.ATTR_JTAG_DEVICE, ConfigurationAttributes.JTAG_DEVICE);

		// These are inherited from the generic implementation.
		// Some might need some trimming.
		{
			configuration.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_UPDATE_THREADLIST_ON_SUSPEND,
					IGDBLaunchConfigurationConstants.DEBUGGER_UPDATE_THREADLIST_ON_SUSPEND_DEFAULT);
		}

		// QEMU GDB server setup
		{
			defaultBoolean = fPersistentPreferences.getGdbServerDoStart();
			configuration.setAttribute(ConfigurationAttributes.DO_START_GDB_SERVER, defaultBoolean);

			defaultString = fPersistentPreferences.getGdbServerExecutable();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_EXECUTABLE, defaultString);

			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_GDB_PORT_NUMBER,
					DefaultPreferences.SERVER_GDB_PORT_NUMBER_DEFAULT);

			String boardName = CProjectAttributes.getCmsisBoardName(configuration);
			String deviceName = CProjectAttributes.getCmsisDeviceName(configuration);

			// If the project has assigned either a board name,
			// or a device name, use them.
			if (((boardName == null || boardName.isEmpty()) && (deviceName == null || deviceName.isEmpty()))) {

				// Otherwise try the names used previously
				boardName = fPersistentPreferences.getQemuBoardName();
				deviceName = fPersistentPreferences.getQemuDeviceName();
			}

			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_BOARD_NAME,
					boardName == null ? "" : boardName);
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_DEVICE_NAME,
					deviceName == null ? "" : deviceName);

			defaultString = fPersistentPreferences.getGdbServerOtherOptions();
			configuration.setAttribute(ConfigurationAttributes.GDB_SERVER_OTHER, defaultString);

			defaultBoolean = fPersistentPreferences.getQemuEnableSemihosting();
			configuration.setAttribute(ConfigurationAttributes.ENABLE_SEMIHOSTING, defaultBoolean);

			configuration.setAttribute(ConfigurationAttributes.SEMIHOSTING_CMDLINE, getProjectName(configuration));

			configuration.setAttribute(ConfigurationAttributes.DO_GDB_SERVER_ALLOCATE_CONSOLE,
					DefaultPreferences.DO_GDB_SERVER_ALLOCATE_CONSOLE_DEFAULT);

			defaultBoolean = fPersistentPreferences.getQemuIsVerbose();
			configuration.setAttribute(ConfigurationAttributes.IS_GDB_SERVER_VERBOSE, defaultBoolean);
		}

		// GDB client setup
		{
			configuration.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME,
					fPersistentPreferences.getGdbClientExecutable());

			defaultString = fPersistentPreferences.getGdbClientOtherOptions();
			configuration.setAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_OPTIONS,
					fDefaultPreferences.getGdbClientOtherOptions());

			defaultString = fPersistentPreferences.getGdbClientCommands();
			configuration.setAttribute(ConfigurationAttributes.GDB_CLIENT_OTHER_COMMANDS, defaultString);
		}

		// Remote Target
		{
			configuration.setAttribute(IGDBJtagConstants.ATTR_USE_REMOTE_TARGET,
					DefaultPreferences.USE_REMOTE_TARGET_DEFAULT);

			configuration.setAttribute(IGDBJtagConstants.ATTR_PORT_NUMBER,
					DefaultPreferences.REMOTE_PORT_NUMBER_DEFAULT);
		}

		// Force thread update
		configuration.setAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_UPDATE_THREADLIST_ON_SUSPEND,
				ConfigurationAttributes.UPDATE_THREAD_LIST_DEFAULT);
	}

	// ------------------------------------------------------------------------
}
