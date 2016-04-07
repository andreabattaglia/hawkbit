/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.common.detailslayout;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.repository.SpPermissionChecker;
import org.eclipse.hawkbit.repository.model.NamedEntity;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEvent;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleSmallNoBorder;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.I18N;
import org.eclipse.hawkbit.ui.utils.SPDateTimeUtil;
import org.eclipse.hawkbit.ui.utils.SPUIComponetIdProvider;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.spring.events.EventBus;

import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * Abstract Layout to show the entity details.
 *
 */
public abstract class AbstractTableDetailsLayout<T extends NamedEntity> extends VerticalLayout {

    private static final long serialVersionUID = 4862529368471627190L;

    @Autowired
    protected I18N i18n;

    @Autowired
    protected transient EventBus.SessionEventBus eventBus;

    @Autowired
    protected SpPermissionChecker permissionChecker;

    protected T selectedBaseEntity;

    private Label caption;

    private Button editButton;

    private TabSheet detailsTab;

    private VerticalLayout detailsLayout;

    private VerticalLayout descriptionLayout;

    private VerticalLayout logLayout;

    private VerticalLayout attributesLayout;

    /**
     * Initialize components.
     */
    @PostConstruct
    protected void init() {
        createComponents();
        buildLayout();
        restoreState();
        eventBus.subscribe(this);
    }

    @PreDestroy
    void destroy() {
        eventBus.unsubscribe(this);
    }

    /**
     * Default implementation to handle a entity event.
     * 
     * @param baseEntityEvent
     *            the event
     */
    protected void onBaseEntityEvent(final BaseEntityEvent<T> baseEntityEvent) {
        final BaseEntityEventType eventType = baseEntityEvent.getEventType();
        if (BaseEntityEventType.SELECTED_ENTITY == eventType || BaseEntityEventType.UPDATED_ENTITY == eventType) {
            UI.getCurrent().access(() -> populateData(baseEntityEvent.getEntity()));
        } else if (BaseEntityEventType.MINIMIZED == eventType) {
            UI.getCurrent().access(() -> setVisible(true));
        } else if (BaseEntityEventType.MAXIMIZED == eventType) {
            UI.getCurrent().access(() -> setVisible(false));
        }
    }

    private void createComponents() {
        /**
         * Default caption is set.Reset on selecting table row.
         */
        caption = createHeaderCaption();
        caption.setImmediate(true);
        caption.setContentMode(ContentMode.HTML);
        caption.setId(getDetailsHeaderCaptionId());

        editButton = SPUIComponentProvider.getButton("", "", "", null, false, FontAwesome.PENCIL_SQUARE_O,
                SPUIButtonStyleSmallNoBorder.class);
        editButton.setId(getEditButtonId());
        editButton.addClickListener(event -> onEdit(event));

        editButton.setEnabled(false);

        detailsTab = SPUIComponentProvider.getDetailsTabSheet();
        detailsTab.setImmediate(true);
        detailsTab.setWidth(98, Unit.PERCENTAGE);
        detailsTab.setHeight(90, Unit.PERCENTAGE);
        detailsTab.addStyleName(SPUIStyleDefinitions.DETAILS_LAYOUT_STYLE);
        detailsTab.setId(getTabSheetId());
        addTabs(detailsTab);
    }

    private void buildLayout() {

        final HorizontalLayout nameEditLayout = new HorizontalLayout();
        nameEditLayout.setWidth(100.0f, Unit.PERCENTAGE);
        nameEditLayout.addComponent(caption);
        nameEditLayout.setComponentAlignment(caption, Alignment.MIDDLE_LEFT);
        if (hasEditPermission()) {
            nameEditLayout.addComponent(editButton);
            nameEditLayout.setComponentAlignment(editButton, Alignment.MIDDLE_RIGHT);
        }
        nameEditLayout.setExpandRatio(caption, 1.0f);
        nameEditLayout.addStyleName(SPUIStyleDefinitions.WIDGET_TITLE);

        addComponent(nameEditLayout);
        setComponentAlignment(nameEditLayout, Alignment.MIDDLE_CENTER);
        addComponent(detailsTab);
        setComponentAlignment(nameEditLayout, Alignment.MIDDLE_CENTER);

        setSizeFull();
        setHeightUndefined();
        addStyleName(SPUIStyleDefinitions.WIDGET_STYLE);
    }

    private Label createHeaderCaption() {
        final Label captionLabel = SPUIComponentProvider.getLabel(getDefaultCaption(),
                SPUILabelDefinitions.SP_WIDGET_CAPTION);
        return captionLabel;
    }

    protected VerticalLayout getTabLayout() {
        final VerticalLayout tabLayout = SPUIComponentProvider.getDetailTabLayout();
        tabLayout.addStyleName("details-layout");
        return tabLayout;
    }

    protected void setName(final String headerCaption, final String value) {
        caption.setValue(HawkbitCommonUtil.getSoftwareModuleName(headerCaption, value));
    }

    private void restoreState() {
        if (onLoadIsTableRowSelected()) {
            populateData(null);
            editButton.setEnabled(true);
        }
        if (onLoadIsTableMaximized()) {
            setVisible(false);
        }
    }

    /**
     * If no data in table (i,e no row selected),then disable the edit button.
     * If row is selected ,enable edit button.
     */
    private void populateData(final T selectedBaseEntity) {
        this.selectedBaseEntity = selectedBaseEntity;
        editButton.setEnabled(selectedBaseEntity != null);
        if (selectedBaseEntity == null) {
            setName(getDefaultCaption(), StringUtils.EMPTY);
        } else {
            setName(getDefaultCaption(), getName());
        }
        populateLog();
        populateDescription();
        populateDetailsWidget();
    }

    protected void updateLogLayout(final VerticalLayout changeLogLayout, final Long lastModifiedAt,
            final String lastModifiedBy, final Long createdAt, final String createdBy, final I18N i18n) {
        changeLogLayout.removeAllComponents();
        changeLogLayout.addComponent(SPUIComponentProvider.createNameValueLabel(i18n.get("label.created.at"),
                createdAt == null ? "" : SPDateTimeUtil.getFormattedDate(createdAt)));

        changeLogLayout.addComponent(SPUIComponentProvider.createNameValueLabel(i18n.get("label.created.by"),
                createdBy == null ? "" : HawkbitCommonUtil.getIMUser(createdBy)));

        if (null != lastModifiedAt) {
            changeLogLayout.addComponent(SPUIComponentProvider.createNameValueLabel(i18n.get("label.modified.date"),
                    SPDateTimeUtil.getFormattedDate(lastModifiedAt)));

            changeLogLayout.addComponent(SPUIComponentProvider.createNameValueLabel(i18n.get("label.modified.by"),
                    lastModifiedBy == null ? "" : HawkbitCommonUtil.getIMUser(lastModifiedBy)));
        }
    }

    protected void updateDescriptionLayout(final String descriptionLabel, final String description) {
        descriptionLayout.removeAllComponents();
        final Label descLabel = SPUIComponentProvider.createNameValueLabel(descriptionLabel,
                HawkbitCommonUtil.trimAndNullIfEmpty(description) == null ? "" : description);
        /**
         * By default text will be truncated based on layout width .so removing
         * it as we need full description.
         */
        descLabel.removeStyleName("label-style");
        descLabel.setId(SPUIComponetIdProvider.DETAILS_DESCRIPTION_LABEL_ID);
        descriptionLayout.addComponent(descLabel);
    }

    /*
     * display Attributes details in Target details.
     */

    protected void updateAttributesLayout(final Target target) {
        if (null != target && null != target.getTargetInfo()
                && null != target.getTargetInfo().getControllerAttributes()) {
            attributesLayout.removeAllComponents();
            for (final Map.Entry<String, String> entry : target.getTargetInfo().getControllerAttributes().entrySet()) {
                final Label conAttributeLabel = SPUIComponentProvider.createNameValueLabel(
                        entry.getKey().concat("  :  "),
                        HawkbitCommonUtil.trimAndNullIfEmpty(entry.getValue()) == null ? "" : entry.getValue());
                conAttributeLabel.setDescription(entry.getKey().concat("  :  ") + entry.getValue());
                conAttributeLabel.addStyleName("label-style");
                attributesLayout.addComponent(conAttributeLabel);

            }
        }
    }

    protected VerticalLayout createLogLayout() {
        logLayout = getTabLayout();
        return logLayout;
    }

    protected VerticalLayout createAttributesLayout() {
        attributesLayout = getTabLayout();
        return attributesLayout;
    }

    protected VerticalLayout createDetailsLayout() {
        detailsLayout = getTabLayout();
        return detailsLayout;
    }

    protected VerticalLayout createDescriptionLayout() {
        descriptionLayout = getTabLayout();
        return descriptionLayout;
    }

    /**
     * Default caption of header to be displayed when no data row selected in
     * table.
     * 
     * @return String
     */
    protected abstract String getDefaultCaption();

    /**
     * Add tabs.
     * 
     * @param detailsTab
     */
    protected abstract void addTabs(final TabSheet detailsTab);

    /**
     * Click listener for edit button.
     * 
     * @param event
     */
    protected abstract void onEdit(Button.ClickEvent event);

    protected abstract String getEditButtonId();

    protected abstract Boolean onLoadIsTableRowSelected();

    protected abstract Boolean onLoadIsTableMaximized();

    protected abstract String getTabSheetId();

    protected abstract Boolean hasEditPermission();

    public VerticalLayout getDetailsLayout() {
        return detailsLayout;
    }

    public VerticalLayout getLogLayout() {
        return logLayout;
    }

    private void populateLog() {
        if (selectedBaseEntity == null) {
            updateLogLayout(getLogLayout(), null, StringUtils.EMPTY, null, null, i18n);
            return;
        }
        updateLogLayout(getLogLayout(), selectedBaseEntity.getLastModifiedAt(), selectedBaseEntity.getLastModifiedBy(),
                selectedBaseEntity.getCreatedAt(), selectedBaseEntity.getCreatedBy(), i18n);
    }

    private void populateDescription() {
        if (selectedBaseEntity != null) {
            updateDescriptionLayout(i18n.get("label.description"), selectedBaseEntity.getDescription());
        } else {
            updateDescriptionLayout(i18n.get("label.description"), null);
        }
    }

    protected abstract void populateDetailsWidget();

    protected Long getSelectedBaseEntityId() {
        return selectedBaseEntity == null ? null : selectedBaseEntity.getId();
    }

    protected abstract String getDetailsHeaderCaptionId();

    protected abstract String getName();

}
