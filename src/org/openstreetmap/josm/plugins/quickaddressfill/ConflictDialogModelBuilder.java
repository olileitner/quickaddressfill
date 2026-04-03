package org.openstreetmap.josm.plugins.quickaddressfill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

final class ConflictDialogModelBuilder {

    static final class DialogRow {
        private final String field;
        private final String existing;
        private final String proposed;

        DialogRow(String field, String existing, String proposed) {
            this.field = field;
            this.existing = existing;
            this.proposed = proposed;
        }

        String getField() {
            return field;
        }

        String getExisting() {
            return existing;
        }

        String getProposed() {
            return proposed;
        }
    }

    static final class DialogModel {
        private final List<DialogRow> rows;

        DialogModel(List<DialogRow> rows) {
            this.rows = rows == null ? List.of() : Collections.unmodifiableList(rows);
        }

        List<DialogRow> getRows() {
            return rows;
        }

        boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    DialogModel build(AddressConflictService.ConflictAnalysis analysis, Function<String, String> valueFormatter) {
        if (analysis == null || analysis.getDifferingFields().isEmpty()) {
            return new DialogModel(List.of());
        }

        Function<String, String> formatter = valueFormatter == null ? this::nullSafe : valueFormatter;
        List<DialogRow> rows = new ArrayList<>();
        for (AddressConflictService.ConflictField field : analysis.getDifferingFields()) {
            rows.add(new DialogRow(
                    field.getKey(),
                    formatter.apply(field.getExistingValue()),
                    formatter.apply(field.getProposedValue())
            ));
        }
        return new DialogModel(rows);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}


