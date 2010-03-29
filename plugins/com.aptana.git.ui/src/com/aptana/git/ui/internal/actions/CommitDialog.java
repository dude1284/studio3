package com.aptana.git.ui.internal.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TableDropTargetEffect;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;

import com.aptana.git.core.model.ChangedFile;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.ui.DiffFormatter;
import com.aptana.git.ui.GitUIPlugin;
import com.aptana.git.ui.actions.RevertAction;

public class CommitDialog extends StatusDialog
{
	private GitRepository gitRepository;
	private Text commitMessage;
	private String fMessage;
	private Table unstagedTable;
	private Table stagedTable;
	private Control draggingFromTable;

	private Image newFileImage;
	private Image deletedFileImage;
	private Image emptyFileImage;
	private Browser diffArea;
	private ChangedFile fLastDiffFile;

	public CommitDialog(Shell parentShell, GitRepository gitRepository)
	{
		super(parentShell);
		Assert.isNotNull(gitRepository, "Must have a non-null git repository!"); //$NON-NLS-1$
		this.gitRepository = gitRepository;
		newFileImage = GitUIPlugin.getImage("icons/obj16/new_file.png"); //$NON-NLS-1$
		deletedFileImage = GitUIPlugin.getImage("icons/obj16/deleted_file.png"); //$NON-NLS-1$
		emptyFileImage = GitUIPlugin.getImage("icons/obj16/empty_file.png"); //$NON-NLS-1$
		fLastDiffFile = null;
	}

	@Override
	protected IDialogSettings getDialogBoundsSettings()
	{
		IDialogSettings compareSettings = GitUIPlugin.getDefault().getDialogSettings();
		String sectionName = this.getClass().getName();
		IDialogSettings dialogSettings = compareSettings.getSection(sectionName);
		if (dialogSettings == null)
		{
			dialogSettings = compareSettings.addNewSection(sectionName);
		}
		return dialogSettings;
	}

	@Override
	protected Control createDialogArea(Composite parent)
	{
		Composite container = (Composite) super.createDialogArea(parent);
		parent.getShell().setText(Messages.CommitDialog_Changes);

		container.setLayout(new GridLayout(1, true));

		createDiffArea(container);

		SashForm sashForm = new SashForm(container, SWT.HORIZONTAL);
		// Make it fill grid, so when we resize it still does...
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashForm.setLayout(new FillLayout());

		createUnstagedFileArea(sashForm);
		createCommitMessageArea(sashForm);
		createStagedFileArea(sashForm);

		sashForm.setSashWidth(5);
		sashForm.setWeights(new int[] { 25, 50, 25 });

		validate();

		return container;
	}

	/**
	 * Override the default implementation to get a bigger commit dialog on large monitors.
	 * 
	 * @see org.eclipse.jface.dialogs.Dialog#getInitialSize()
	 */
	protected Point getInitialSize()
	{
		IDialogSettings dialogSettings = getDialogBoundsSettings();
		try
		{
			dialogSettings.getInt("DIALOG_WIDTH"); //$NON-NLS-1$
		}
		catch (NumberFormatException e)
		{
			// The dialog settings are empty, so we need to compute the initial
			// size according to the size of the monitor. Large monitors will get a bigger commit dialog.
			Composite parent = getShell().getParent();
			Monitor monitor = getShell().getDisplay().getPrimaryMonitor();
			if (parent != null)
			{
				monitor = parent.getMonitor();
			}
			Rectangle monitorBounds = monitor.getClientArea();
			return new Point((int) (0.618 * monitorBounds.width), (int) (0.618 * monitorBounds.height));
		}
		return super.getInitialSize();
	}

	private void createDiffArea(Composite container)
	{
		diffArea = new Browser(container, SWT.BORDER);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 300;
		diffArea.setLayoutData(data);
		diffArea.setText(Messages.CommitDialog_NoFileSelected);
	}

	private void createUnstagedFileArea(SashForm sashForm)
	{
		createFileArea(sashForm, false);
	}

	private void createStagedFileArea(SashForm sashForm)
	{
		createFileArea(sashForm, true);
	}

	private void createFileArea(SashForm sashForm, boolean staged)
	{
		Composite composite = new Composite(sashForm, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));
		StagingButtons buttons = null;
		if (staged)
		{
			buttons = new StagingButtons(composite, Messages.CommitDialog_UnstageAllMarker,
					Messages.CommitDialog_UnstageAll, Messages.CommitDialog_UnstageSelectedMarker,
					Messages.CommitDialog_UnstageSelected);
			createTableComposite(composite, staged);
			buttons.setTable(stagedTable, staged);
		}
		else
		{
			createTableComposite(composite, staged);
			buttons = new StagingButtons(composite, Messages.CommitDialog_StageAllMarker,
					Messages.CommitDialog_StageAll, Messages.CommitDialog_StageSelectedMarker,
					Messages.CommitDialog_StageSelected);
			buttons.setTable(unstagedTable, staged);
		}
	}

	// Creates a table with a title label on top of it.
	private Composite createTableComposite(Composite parent, boolean staged)
	{
		Composite tableComp = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 1;
		layout.marginWidth = 1;
		tableComp.setLayout(layout);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 200;
		data.widthHint = 250;
		tableComp.setLayoutData(data);
		Label label = new Label(tableComp, SWT.NONE);
		Table table = null;
		if (staged)
		{
			label.setText(Messages.CommitDialog_StagedChanges);
			table = createTable(tableComp, true);
		}
		else
		{
			label.setText(Messages.CommitDialog_UnstagedChanges);
			table = createTable(tableComp, false);
		}
		if (staged)
			stagedTable = table;
		else
			unstagedTable = table;
		return tableComp;
	}

	private void createCommitMessageArea(SashForm sashForm)
	{
		Composite msgComp = new Composite(sashForm, SWT.NONE);
		GridLayout layout = new GridLayout(1, true);
		layout.horizontalSpacing = 0;
		layout.marginWidth = 0;
		msgComp.setLayout(layout);
		Label messageLabel = new Label(msgComp, SWT.NONE);
		messageLabel.setText(Messages.CommitDialog_MessageLabel);
		commitMessage = new Text(msgComp, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		commitMessage.addKeyListener(new KeyListener()
		{

			public void keyReleased(KeyEvent e)
			{
				validate();
			}

			public void keyPressed(KeyEvent e)
			{
			}
		});
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 200;
		data.widthHint = 300;
		commitMessage.setLayoutData(data);
	}

	private Table createTable(Composite composite, final boolean staged)
	{
		// TODO Make list entries be able to be truncated when too long to fit, like GitX does
		// TODO Sort list entries
		Table table = new Table(composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.heightHint = 200;
		data.widthHint = 250;
		table.setLayoutData(data);
		String[] titles = { " ", Messages.CommitDialog_PathColumnLabel }; //$NON-NLS-1$
		for (int i = 0; i < titles.length; i++)
		{
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(titles[i]);
		}
		for (ChangedFile file : gitRepository.index().changedFiles())
		{
			boolean match = false;
			if (staged && file.hasStagedChanges())
				match = true;
			else if (!staged && file.hasUnstagedChanges())
				match = true;

			if (match)
			{
				createTableItem(table, file);
			}
		}
		packTable(table);

		// Drag and Drop
		// FIXME If user drags and drops while we're still crunching on last drag/drop then we end up hanging
		// Seems to be related to manipulating the table here before we receive the index changed callback
		Transfer[] types = new Transfer[] { TextTransfer.getInstance() };

		// Drag Source
		DragSource source = new DragSource(table, DND.DROP_MOVE);
		source.setTransfer(types);
		source.addDragListener(new DragSourceAdapter()
		{
			public void dragStart(DragSourceEvent event)
			{
				DragSource ds = (DragSource) event.widget;
				draggingFromTable = ds.getControl();
			}

			public void dragSetData(DragSourceEvent event)
			{
				// Get the selected items in the drag source
				DragSource ds = (DragSource) event.widget;
				Table table = (Table) ds.getControl();
				TableItem[] selection = table.getSelection();
				// Create a comma separated string of the paths of the changed files we're dragging
				StringBuffer buff = new StringBuffer();
				for (int i = 0, n = selection.length; i < n; i++)
				{
					buff.append(selection[i].getText(1)).append(","); //$NON-NLS-1$
				}
				if (buff.length() > 0)
					buff.deleteCharAt(buff.length() - 1);
				event.data = buff.toString();
			}
		});

		// Create the drop target
		DropTarget target = new DropTarget(table, DND.DROP_MOVE);
		target.setTransfer(types);
		if (table.getItemCount() == 0)
			target.setDropTargetEffect(null);
		target.addDropListener(new DropTargetAdapter()
		{
			public void dropAccept(DropTargetEvent event)
			{
				DropTarget dp = (DropTarget) event.widget;
				if (dp.getControl() == draggingFromTable)
				{
					event.detail = DND.DROP_NONE;
				}
			}

			public void dragEnter(DropTargetEvent event)
			{
				// Allow dropping text only
				for (int i = 0, n = event.dataTypes.length; i < n; i++)
				{
					if (TextTransfer.getInstance().isSupportedType(event.dataTypes[i]))
					{
						event.currentDataType = event.dataTypes[i];
					}
				}
				event.operations = DND.DROP_MOVE;
			}

			public void dragOver(DropTargetEvent event)
			{
				event.feedback = DND.FEEDBACK_SCROLL;
			}

			public void drop(DropTargetEvent event)
			{
				if (!TextTransfer.getInstance().isSupportedType(event.currentDataType))
					return;
				// Get the dropped data
				String data = (String) event.data;
				// Translate the comma delimited paths back into the matching ChangedFile objects
				Map<String, ChangedFile> draggedFiles = new HashMap<String, ChangedFile>();
				StringTokenizer tokenizer = new StringTokenizer(data, ","); //$NON-NLS-1$
				while (tokenizer.hasMoreTokens())
				{
					String path = tokenizer.nextToken();
					ChangedFile changedFile = findChangedFile(path);
					draggedFiles.put(path, changedFile);
				}

				// Actually stage or unstage the files
				if (staged)
				{
					stageFiles(draggedFiles);
				}
				else
				{
					unstageFiles(draggedFiles);
				}
			}
		});

		table.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				super.widgetSelected(e);
				if (e.item == null)
					return;
				String filePath = ((TableItem) e.item).getText(1);
				updateDiff(staged, filePath);
			}
		});
		// Allow double-clicking to toggle staged/unstaged
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseDoubleClick(MouseEvent e)
			{
				if (e.getSource() == null)
					return;
				Table table = (Table) e.getSource();
				TableItem[] selected = table.getSelection();
				moveItems(staged, selected);
			}
		});

		if (!staged)
		{
			final Table myTable = table;
			MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
			menuMgr.setRemoveAllWhenShown(true);
			menuMgr.addMenuListener(new IMenuListener()
			{
				public void menuAboutToShow(IMenuManager manager)
				{

					TableItem[] selected = myTable.getSelection();
					List<IResource> files = new ArrayList<IResource>();
					final List<String> filePaths = new ArrayList<String>();
					for (TableItem item : selected)
					{
						String filePath = item.getText(1);

						String workingDirectory = gitRepository.workingDirectory();

						IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
								new Path(workingDirectory).append(filePath));
						if (file != null)
						{
							files.add(file);
							filePaths.add(filePath);
						}
					}
					RevertAction revertAction = new RevertAction()
					{
						// need to remove the file(s) from staged table once action runs
						@Override
						protected void doOperation(GitRepository repo, final List<ChangedFile> changedFiles)
						{
							super.doOperation(repo, changedFiles);
							PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
							{

								@Override
								public void run()
								{
									// If this file was shown in diff area, we need to blank the diff area!
									if (fLastDiffFile != null)
									{
										for (ChangedFile file : changedFiles)
										{
											if (file != null && file.equals(fLastDiffFile))
											{
												updateDiff(null, Messages.CommitDialog_NoFileSelected);
											}
										}
									}
									removeDraggedFilesFromSource(unstagedTable, filePaths);
								}
							});
						}
					};
					revertAction.selectionChanged(null, new StructuredSelection(files));
					manager.add(revertAction);
					// Other plug-ins can contribute there actions here
					manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
				}
			});
			Menu menu = menuMgr.createContextMenu(table);
			table.setMenu(menu);
		}

		return table;
	}

	protected synchronized void unstageFiles(final Map<String, ChangedFile> files)
	{
		toggleStageStatus(files, false);
		gitRepository.index().unstageFiles(files.values());
	}

	protected synchronized void stageFiles(final Map<String, ChangedFile> files)
	{
		toggleStageStatus(files, true);
		gitRepository.index().stageFiles(files.values());
	}

	private void toggleStageStatus(Map<String, ChangedFile> files, boolean stage)
	{
		Table to = stagedTable;
		Table from = unstagedTable;
		if (!stage)
		{
			from = stagedTable;
			to = unstagedTable;
		}
		to.setRedraw(false);
		for (ChangedFile changedFile : files.values())
		{
			createTableItem(to, changedFile); // add it to our new table
		}
		packTable(to);
		to.setRedraw(true);
		to.redraw();
		removeDraggedFilesFromSource(from, files.keySet());
		workaroundEmptyTableDropEffectBug(from);
		validate();
	}

	/**
	 * Update the diff area.
	 * 
	 * @param staged
	 * @param filePath
	 * @see #updateDiff(ChangedFile, String)
	 */
	private void updateDiff(final boolean staged, String filePath)
	{
		ChangedFile file = findChangedFile(filePath);
		if (file == null)
			return;
		if (gitRepository.index().hasBinaryAttributes(file) && !file.getStatus().equals(ChangedFile.Status.DELETED))
		{
			// Special code to draw the image if the binary file is an image
			String[] imageExtensions = new String[] { ".png", ".gif", ".jpeg", ".jpg", ".ico" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			for (String extension : imageExtensions)
			{
				if (file.getPath().endsWith(extension))
				{
					String fullPath = new File(gitRepository.workingDirectory(), file.getPath()).getAbsolutePath();
					updateDiff(file, "<img src=\"" + fullPath + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
					return;
				}
			}
		}
		// Don't recalc if it's the same file as we are already showing
		if (fLastDiffFile != null && file.equals(fLastDiffFile))
			return;

		String diff = gitRepository.index().diffForFile(file, staged, 3);
		try
		{
			diff = DiffFormatter.toHTML(file.getPath(), diff);
		}
		catch (Throwable t)
		{
			GitUIPlugin.logError("Failed to turn diff into HTML", t); //$NON-NLS-1$
		}
		updateDiff(file, diff);
	}

	/**
	 * Update the diff area.
	 * 
	 * @param file
	 * @param diff
	 * @see #updateDiff(boolean, String)
	 */
	protected void updateDiff(ChangedFile file, String diff)
	{
		if (diffArea != null && !diffArea.isDisposed())
		{
			diffArea.setText(diff);
			fLastDiffFile = file;
		}
	}

	protected ChangedFile findChangedFile(String path)
	{
		List<ChangedFile> changedFiles = gitRepository.index().changedFiles();
		for (ChangedFile changedFile : changedFiles)
		{
			if (changedFile.getPath().equals(path))
			{
				return changedFile;
			}
		}
		return null;
	}

	/**
	 * Creates a table item for a ChangedFile in Git
	 * 
	 * @param table
	 * @param file
	 */
	protected void createTableItem(Table table, ChangedFile file)
	{
		TableItem item = new TableItem(table, SWT.NONE);
		Image image = emptyFileImage;
		String text = Messages.CommitDialog_modified;
		if (file.getStatus() == ChangedFile.Status.DELETED)
		{
			image = deletedFileImage;
			text = Messages.CommitDialog_deleted;
		}
		else if (file.getStatus() == ChangedFile.Status.NEW)
		{
			image = newFileImage;
			text = Messages.CommitDialog_new;
		}
		item.setText(0, text);
		item.setImage(0, image);
		item.setText(1, file.getPath());
	}

	private void packTable(Table table)
	{
		for (int i = 0; i < table.getColumnCount(); i++)
		{
			table.getColumn(i).pack();
		}
	}

	protected void validate()
	{
		if (commitMessage.getText().length() < 3)
		{
			updateStatus(new Status(IStatus.ERROR, GitUIPlugin.getPluginId(), Messages.CommitDialog_EnterMessage_Error));
			return;
		}
		if (stagedTable.getItemCount() == 0)
		{
			updateStatus(new Status(IStatus.ERROR, GitUIPlugin.getPluginId(),
					Messages.CommitDialog_StageFilesFirst_Error));
			return;
		}
		if (gitRepository.hasUnresolvedMergeConflicts())
		{
			updateStatus(new Status(IStatus.ERROR, GitUIPlugin.getPluginId(), Messages.CommitDialog_CannotMerge_Error));
			return;
		}
		fMessage = commitMessage.getText();
		updateStatus(Status.OK_STATUS);
	}

	// TODO Change way dialog is composed to push buttons into commit message area like GitX?

	@Override
	protected Button createButton(Composite parent, int id, String label, boolean defaultButton)
	{
		if (id == IDialogConstants.OK_ID)
			label = Messages.CommitDialog_CommitButton_Label;
		return super.createButton(parent, id, label, defaultButton);
	}

	public String getCommitMessage()
	{
		return fMessage;
	}

	/**
	 * Find the table items we just dragged over and remove them from the source table
	 * 
	 * @param sourceTable
	 * @param draggedFiles
	 */
	protected void removeDraggedFilesFromSource(Table sourceTable, Collection<String> draggedFiles)
	{
		List<Integer> toRemove = new ArrayList<Integer>();
		TableItem[] items = sourceTable.getItems();
		for (int i = 0; i < items.length; i++)
		{
			TableItem item = items[i];
			if (draggedFiles.contains(item.getText(1)))
			{
				toRemove.add(i);
			}
		}
		int[] primitive = new int[toRemove.size()];
		int x = 0;
		for (Integer object : toRemove)
		{
			primitive[x++] = object.intValue();
		}
		sourceTable.setRedraw(false);
		sourceTable.remove(primitive);
		packTable(sourceTable);
		sourceTable.setRedraw(true);
		sourceTable.redraw();
	}

	/**
	 * HACK Workaround bug where drag and drop breaks horribly on a table when it's empty
	 * 
	 * @param sourceDragTable
	 */
	private void workaroundEmptyTableDropEffectBug(Table sourceDragTable)
	{
		DropTarget dtarget = (DropTarget) sourceDragTable.getData(DND.DROP_TARGET_KEY);
		if (dtarget == null)
			return;
		if (sourceDragTable.getItemCount() == 0)
		{
			dtarget.setDropTargetEffect(null);
		}
		else if (dtarget.getDropTargetEffect() == null)
		{
			dtarget.setDropTargetEffect(new TableDropTargetEffect(sourceDragTable));
		}
	}

	@Override
	protected boolean isResizable()
	{
		return true;
	}

	/*
	 * Stage or Un-Stage the selected items (files).
	 * @param staged
	 * @param selected
	 */
	private void moveItems(final boolean staged, TableItem[] selected)
	{
		Map<String, ChangedFile> selectedFiles = new HashMap<String, ChangedFile>();
		for (TableItem item : selected)
		{
			String path = item.getText(1);
			ChangedFile file = findChangedFile(path);
			if (file == null)
				continue;
			selectedFiles.put(path, file);
		}
		if (selectedFiles.isEmpty())
			return;

		// Actually stage or unstage the files
		if (staged)
		{
			unstageFiles(selectedFiles);
		}
		else
		{
			stageFiles(selectedFiles);
		}
	}

	/*
	 * This class will create buttons next to the tables in order to allow staging and un-staging operations.
	 */
	private class StagingButtons extends SelectionAdapter
	{
		private String doAllLabel;
		private String doAllTooltip;
		private String doSelectionLabel;
		private String doSelectionTooltip;
		private Table table;
		private boolean staged;
		private Button doSelectionBt;

		/**
		 * Constructs a new StagingButtons instance.
		 * 
		 * @param parent
		 * @param doAllLabel
		 * @param doAllTooltip
		 * @param doSelectionLabel
		 * @param doSelectionTooltip
		 */
		protected StagingButtons(Composite parent, String doAllLabel, String doAllTooltip, String doSelectionLabel,
				String doSelectionTooltip)
		{
			this.doAllLabel = doAllLabel;
			this.doAllTooltip = doAllTooltip;
			this.doSelectionLabel = doSelectionLabel;
			this.doSelectionTooltip = doSelectionTooltip;
			createComponent(parent);
		}

		/**
		 * Set the table (staged or un-staged) that this component controls.
		 * 
		 * @param table
		 * @param staged
		 */
		protected void setTable(Table table, boolean staged)
		{
			this.table = table;
			this.staged = staged;
			table.addSelectionListener(this);
			updateSelectionButton();
		}

		protected void createComponent(Composite parent)
		{
			Composite comp = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout(1, true);
			layout.horizontalSpacing = 0;
			layout.marginWidth = 1;
			comp.setLayout(layout);
			Button doAllBt = new Button(comp, SWT.PUSH | SWT.FLAT);
			doAllBt.setText(doAllLabel);
			doAllBt.setToolTipText(doAllTooltip);
			doSelectionBt = new Button(comp, SWT.PUSH | SWT.FLAT);
			doSelectionBt.setText(doSelectionLabel);
			doSelectionBt.setToolTipText(doSelectionTooltip);

			GridData data = new GridData(GridData.FILL_HORIZONTAL);
			doAllBt.setLayoutData(data);
			data = new GridData(GridData.FILL_HORIZONTAL);
			doSelectionBt.setLayoutData(data);

			// minimize the width of this component
			Point doAllSize = doAllBt.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			Point doSelectionSize = doSelectionBt.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			Point bigger = null;
			if (doAllSize.x > doSelectionSize.x)
				bigger = doAllSize;
			else
				bigger = doSelectionSize;
			data = new GridData(SWT.CENTER, SWT.CENTER, false, false);
			data.widthHint = bigger.x + 10;
			comp.setLayoutData(data);

			// Hook the actions

			doAllBt.addSelectionListener(new SelectionAdapter()
			{
				public void widgetSelected(SelectionEvent e)
				{
					if (table != null)
					{
						moveItems(staged, table.getItems());
						updateSelectionDiff(-1);
						updateSelectionButton();
					}
				}
			});
			doSelectionBt.addSelectionListener(new SelectionAdapter()
			{
				public void widgetSelected(SelectionEvent e)
				{
					if (table != null)
					{
						int selectionIndex = table.getSelectionIndex();
						TableItem[] selection = table.getSelection();
						moveItems(staged, selection);
						updateSelectionDiff(selectionIndex);
						updateSelectionButton();
					}
				}
			});
		}

		@Override
		public void widgetSelected(SelectionEvent e)
		{
			updateSelectionButton();
		}

		// Enable the doSelectionBt when the table has a selection
		private void updateSelectionButton()
		{
			doSelectionBt.setEnabled(table.getSelectionCount() > 0);
		}

		private void updateSelectionDiff(int previousSelectionIndex)
		{
			// Select the next file in line (if exists)
			if (table.getItemCount() > 0 && previousSelectionIndex > -1)
			{
				if (table.getItemCount() > previousSelectionIndex)
				{
					table.select(previousSelectionIndex);
				}
				else
				{
					table.select(table.getItemCount() - 1);
				}
				String filePath = table.getSelection()[0].getText(1);
				updateDiff(!staged, filePath);
			}
			else
			{
				updateDiff(null, Messages.CommitDialog_NoFileSelected);
			}
		}
	}
}
