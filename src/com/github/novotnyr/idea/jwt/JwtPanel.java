package com.github.novotnyr.idea.jwt;

import com.github.novotnyr.idea.jwt.action.AddClaimActionButtonController;
import com.github.novotnyr.idea.jwt.action.EditClaimActionButtonUpdater;
import com.github.novotnyr.idea.jwt.core.Jwt;
import com.github.novotnyr.idea.jwt.core.NamedClaim;
import com.github.novotnyr.idea.jwt.core.StringSecret;
import com.github.novotnyr.idea.jwt.datatype.DataTypeRegistry;
import com.github.novotnyr.idea.jwt.ui.ClaimTableTranferHandler;
import com.github.novotnyr.idea.jwt.ui.UiUtils;
import com.github.novotnyr.idea.jwt.validation.ClaimError;
import com.github.novotnyr.idea.jwt.validation.JwtValidator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;

public class JwtPanel extends JPanel {
    private JLabel headerLabel = new JLabel("Header");

    private JwtHeaderTableModel headerTableModel;

    private JBTable headerTable = new JBTable();

    private JLabel payloadLabel = new JLabel("Payload");

    private JwtClaimsTableModel claimsTableModel;

    private JBTable claimsTable = new JBTable();

    private JPanel claimsTablePanel;

    private JLabel signatureLabel = new JLabel("Sign/verify signature with secret:");

    private JTextField secretTextField = new JTextField();

    private JButton validateButton = new JButton("Validate");

    private Jwt jwt;

    private AddClaimActionButtonController addClaimActionButtonController;

    private EditClaimActionButtonUpdater editClaimActionButtonUpdater;

    public JwtPanel() {
        setLayout(new GridBagLayout());

        GridBagConstraints cc = new GridBagConstraints();
        cc.fill = GridBagConstraints.HORIZONTAL;
        cc.weightx = 1;
        cc.weighty = 0;
        cc.anchor = GridBagConstraints.FIRST_LINE_START;
        cc.gridx = 0;
        cc.gridy = 0;
        cc.insets = JBUI.insets(5);
        add(this.headerLabel, cc);
        this.headerLabel.setVisible(false);

        cc.gridy++;
        add(this.headerTable, cc);

        cc.gridy++;
        add(this.payloadLabel, cc);
        this.payloadLabel.setVisible(false);

        cc.gridy++;
        cc.weighty = 1;
        cc.ipady = 50;
        cc.fill = GridBagConstraints.BOTH;
        this.claimsTable.setName(Constants.CLAIMS_TABLE_NAME);
        add(this.claimsTablePanel = configureClaimsTableActions(), cc);
        configureClaimsTablePopup(this.claimsTable);
        configureClipboardCopy(this.claimsTable);

        cc.gridy++;
        cc.weighty = 0;
        cc.ipady = 0;
        cc.fill = GridBagConstraints.HORIZONTAL;
        add(this.signatureLabel, cc);

        cc.gridy++;
        add(this.secretTextField, cc);
        configureSecretTextFieldListeners(this.secretTextField);

        cc.gridy++;
        add(this.validateButton, cc);
        this.validateButton.setEnabled(false);
        this.validateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onValidateButtonClick(e);
            }
        });

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent mouseEvent) {
                return onClaimsTableDoubleClick(mouseEvent);
            };
        }.installOn(this.claimsTable);
    }


    private void configureClipboardCopy(JBTable claimsTable) {
        this.claimsTable.setTransferHandler(new ClaimTableTranferHandler());
    }

    private JPanel configureClaimsTableActions() {
        this.addClaimActionButtonController = new AddClaimActionButtonController() {
            @Override
            public void onClaimTypeSelected(DataTypeRegistry.DataType dataType) {
                onNewAction(dataType);
            }
        };

        this.editClaimActionButtonUpdater = new EditClaimActionButtonUpdater();

        this.claimsTablePanel = ToolbarDecorator.createDecorator(this.claimsTable)
                .disableRemoveAction()
                .disableUpDownActions()
                .addExtraAction(new AnActionButton("Copy as JSON", AllIcons.FileTypes.Json) {
                    @Override
                    public void actionPerformed(AnActionEvent anActionEvent) {
                        onCopyAsJsonActionPerformed(anActionEvent);
                    }
                })
                .setEditAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton anActionButton) {
                        onEditAction(anActionButton);
                    }
                })
                .setEditActionUpdater(editClaimActionButtonUpdater)
                .setAddAction(addClaimActionButtonController)
                .setAddActionUpdater(addClaimActionButtonController)
                .createPanel();
        return this.claimsTablePanel;
    }



    private void configureClaimsTablePopup(JBTable table) {
        JPopupMenu popupMenu = new JPopupMenu();
        UiUtils.configureTableRowSelectionOnPopup(popupMenu);

        JMenuItem copyValueMenuItem = new JMenuItem("Copy value (as string)");
        copyValueMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JwtPanel.this.onCopyValueMenuItemActionPerformed(e);
            }
        });
        popupMenu.add(copyValueMenuItem);

        JMenuItem copyAsKeyAndValueMenuItem = new JMenuItem(new AbstractAction("Copy value (as key=value)") {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCopyKeyAndValueMenuItemActionPerformed(e);
            }
        });
        popupMenu.add(copyAsKeyAndValueMenuItem);

        this.claimsTable.setComponentPopupMenu(popupMenu);
    }

    private void configureSecretTextFieldListeners(JTextField secretTextField) {
        secretTextField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent documentEvent) {
                onSecretTextFieldTextChanged(documentEvent);
            }
        });
    }

    private void editClaimAtRow(int row) {
        NamedClaim<?> claim = claimsTableModel.getClaimAt(row);
        showClaimDialog(claim, ClaimDialog.Mode.EDIT);
    }


    private void onCopyAsJsonActionPerformed(AnActionEvent anActionEvent) {
        TextTransferable textTransferable = new TextTransferable(JwtHelper.prettyUnbase64Json(this.jwt.getPayloadString()));
        CopyPasteManagerEx.getInstanceEx().setContents(textTransferable);
    }


    public void onCopyValueMenuItemActionPerformed(ActionEvent e) {
        int selectedRowIndex = this.claimsTable.getSelectedRow();
        Object claimValue = this.claimsTableModel.getValueAt(selectedRowIndex, 1);
        TextTransferable textTransferable = new TextTransferable(claimValue.toString());
        CopyPasteManagerEx.getInstanceEx().setContents(textTransferable);
    }

    public void onCopyKeyAndValueMenuItemActionPerformed(ActionEvent e) {
        int selectedRowIndex = this.claimsTable.getSelectedRow();
        Object claimName = this.claimsTableModel.getValueAt(selectedRowIndex, 0);
        Object claimValue = this.claimsTableModel.getValueAt(selectedRowIndex, 1);
        TextTransferable textTransferable = new TextTransferable(claimName + "=" + claimValue.toString());

        CopyPasteManagerEx.getInstanceEx().setContents(textTransferable);
    }

    private void onValidateButtonClick(ActionEvent e) {
        JwtValidator jwtValidator = new JwtValidator();

        String secret = this.secretTextField.getText();
        jwtValidator.validate(this.jwt, secret);
        this.claimsTableModel.setClaimErrors(jwtValidator.getClaimErrors());
        if(jwtValidator.hasSignatureError()) {
            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder(jwtValidator.getSignatureError().getMessage(), MessageType.ERROR, null)
                    .setFadeoutTime(7500)
                    .createBalloon()
                    .show(RelativePoint.getNorthWestOf(this.secretTextField),
                            Balloon.Position.atRight);
        } else {
            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder("Signature is valid", MessageType.INFO, null)
                    .setFadeoutTime(7500)
                    .createBalloon()
                    .show(RelativePoint.getNorthWestOf(this.secretTextField),
                            Balloon.Position.atRight);
        }
    }

    private boolean onClaimsTableDoubleClick(MouseEvent mouseEvent) {
        if(!hasSecret()) {
            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder("Cannot edit claims when a secret is empty", MessageType.WARNING, null)
                    .setFadeoutTime(7500)
                    .createBalloon()
                    .show(RelativePoint.getNorthWestOf(this.secretTextField),
                            Balloon.Position.atRight);
            return true;
        }

        int selectedRow = claimsTable.rowAtPoint(mouseEvent.getPoint());
        if(selectedRow < 0) {
            return true;
        }
        editClaimAtRow(selectedRow);

        return true;
    }

    private void onEditAction(AnActionButton anActionButton) {
        int selectedRow = this.claimsTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        editClaimAtRow(selectedRow);
    }

    private void onNewAction(DataTypeRegistry.DataType dataType) {
        NamedClaim<?> claim = ClaimUtils.newEmptyClaim(dataType);
        showClaimDialog(claim, ClaimDialog.Mode.NEW);
    }

    private void showClaimDialog(NamedClaim<?> claim, ClaimDialog.Mode mode) {
        ClaimDialog claimDialog = new ClaimDialog(claim, mode);
        if(claimDialog.showAndGet()) {
            NamedClaim<?> updatedClaim = claimDialog.getClaim();
            this.jwt.setSigningCredentials(new StringSecret(getSecret()));
            this.jwt.setPayloadClaim(updatedClaim);
            Jwt oldJwt = this.jwt;
            setJwt(this.jwt);
            firePropertyChange("jwt", null, this.jwt);
        }
    }

    private void onSecretTextFieldTextChanged(DocumentEvent documentEvent) {
        boolean secretIsPresent = documentEvent.getDocument().getLength() > 0;

        DataContext dataContext = SimpleDataContext.getSimpleContext(Constants.DataKeys.SECRET_IS_PRESENT.getName(), secretIsPresent);
        AnActionEvent event = AnActionEvent.createFromDataContext("place", null, dataContext);
        this.addClaimActionButtonController.isEnabled(event);
        this.editClaimActionButtonUpdater.isEnabled(event);
    }


    public void setJwt(Jwt jwt) {
        this.jwt = jwt;

        this.headerLabel.setVisible(true);
        this.payloadLabel.setVisible(true);
        this.validateButton.setEnabled(true);

        this.headerTableModel = new JwtHeaderTableModel(jwt);
        this.headerTable.setModel(this.headerTableModel);

        this.claimsTableModel = new JwtClaimsTableModel(jwt);
        this.claimsTableModel.setClaimErrors(validateClaims(jwt));
        this.claimsTable.setModel(this.claimsTableModel);
        this.claimsTable.setDefaultRenderer(Object.class, this.claimsTableModel);

    }

    private List<ClaimError> validateClaims(Jwt jwt) {
        return new JwtValidator().validateClaims(jwt).getClaimErrors();
    }

    public String getSecret() {
        return this.secretTextField.getText();
    }

    public boolean hasSecret() {
        return getSecret() != null && ! getSecret().isEmpty();
    }

    public JTextField getSecretTextField() {
        return secretTextField;
    }
}
