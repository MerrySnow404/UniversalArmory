package com.sighs.universalarmory.armory;

import com.tacz.guns.resource.pojo.data.gun.GunData;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ArmoryQuery {
    private ArmoryQuery() {
    }

    public static Page select(List<ArmoryEntry> entries, Filter filter, Set<String> favoriteKeys,
                              Sort sort, boolean descending, int requestedPage, int requestedSize) {
        List<ArmoryEntry> selected = new ArrayList<>();
        for (ArmoryEntry entry : entries) {
            if (matches(entry, filter, favoriteKeys)) {
                selected.add(entry);
            }
        }
        Comparator<ArmoryEntry> comparator = switch (sort) {
            case NAME -> Comparator.comparing(ArmoryEntry::name, String.CASE_INSENSITIVE_ORDER);
            case ID -> Comparator.comparing(entry -> String.valueOf(entry.resourceId()));
            case QUANTITY -> Comparator.comparing(ArmoryEntry::quantity);
            case ATTACHMENTS -> Comparator.comparingInt(ArmoryEntry::attachmentCount);
            case DAMAGE -> Comparator.comparingDouble(entry -> damage(entry.gunData()));
            case RPM -> Comparator.comparingInt(entry -> entry.gunData() == null ? 0 : entry.gunData().getRoundsPerMinute());
            case WEIGHT -> Comparator.comparingDouble(entry -> entry.gunData() == null ? 0 : entry.gunData().getWeight());
            case RECENT -> Comparator.comparingInt(ArmoryEntry::recentOrder);
        };
        if (descending) {
            comparator = comparator.reversed();
        }
        selected.sort(comparator.thenComparing(ArmoryEntry::key));
        int size = Math.max(1, Math.min(96, requestedSize));
        int pageCount = Math.max(1, (int) ((selected.size() + (long) size - 1) / size));
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = page * size;
        int end = (int) Math.min(selected.size(), start + (long) size);
        return new Page(List.copyOf(selected.subList(start, end)), selected.size(), page, pageCount, size);
    }

    private static boolean matches(ArmoryEntry entry, Filter filter, Set<String> favoriteKeys) {
        if (!filter.kinds.isEmpty() && !filter.kinds.contains(entry.kind())) {
            return false;
        }
        if (!filter.types.isEmpty() && !filter.types.contains(entry.type())) {
            return false;
        }
        if (!filter.namespaces.isEmpty() && !filter.namespaces.contains(entry.namespace())) {
            return false;
        }
        if (!filter.ammoIds.isEmpty() && !filter.ammoIds.contains(entry.ammoId())) {
            return false;
        }
        boolean collected = entry.quantity().signum() > 0;
        if (filter.ownership == Ownership.COLLECTED && !collected
                || filter.ownership == Ownership.MISSING && collected
                || filter.favoriteOnly && !favoriteKeys.contains(entry.key())
                && !favoriteKeys.contains(ArmoryItemIdentity.resourceKey(entry.stack()))) {
            return false;
        }
        if (filter.attachments == Attachments.BARE && entry.attachmentCount() > 0
                || filter.attachments == Attachments.MODIFIED && entry.attachmentCount() == 0) {
            return false;
        }
        if (filter.locked == Toggle.YES && !entry.attachmentLocked()
                || filter.locked == Toggle.NO && entry.attachmentLocked()) {
            return false;
        }
        if (entry.quantity().compareTo(filter.minimumQuantity) < 0
                || filter.maximumQuantity != null && entry.quantity().compareTo(filter.maximumQuantity) > 0) {
            return false;
        }
        GunData gun = entry.gunData();
        if (!filter.damage.contains(damage(gun))
                || !filter.rpm.contains(gun == null ? 0 : gun.getRoundsPerMinute())
                || !filter.weight.contains(gun == null ? 0 : gun.getWeight())) {
            return false;
        }
        String searchable = (entry.name() + " " + entry.resourceId() + " " + entry.type() + " " + entry.ammoId())
                .toLowerCase(Locale.ROOT);
        for (String term : filter.search.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (!searchable.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private static double damage(GunData gun) {
        return gun == null || gun.getBulletData() == null ? 0 : gun.getBulletData().getDamageAmount();
    }

    public record Filter(String search, Set<ArmoryItemIdentity.Kind> kinds, Set<String> types,
                         Set<String> namespaces, Set<String> ammoIds, Ownership ownership,
                         Attachments attachments, Toggle locked, boolean favoriteOnly,
                         BigInteger minimumQuantity, BigInteger maximumQuantity,
                         Range damage, Range rpm, Range weight) {
        public Filter {
            if (search == null || ownership == null || attachments == null || locked == null
                    || minimumQuantity == null || minimumQuantity.signum() < 0
                    || maximumQuantity != null && maximumQuantity.compareTo(minimumQuantity) < 0
                    || damage == null || rpm == null || weight == null) {
                throw new IllegalArgumentException("Invalid inventory filter");
            }
            kinds = Set.copyOf(kinds);
            types = Set.copyOf(types);
            namespaces = Set.copyOf(namespaces);
            ammoIds = Set.copyOf(ammoIds);
        }

        public static Filter all() {
            Range any = new Range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return new Filter("", Set.of(), Set.of(), Set.of(), Set.of(), Ownership.ANY,
                    Attachments.ANY, Toggle.ANY, false, BigInteger.ZERO, null, any, any, any);
        }
    }

    public record Range(double minimum, double maximum) {
        public Range {
            if (Double.isNaN(minimum) || Double.isNaN(maximum) || minimum > maximum) {
                throw new IllegalArgumentException("Invalid filter range");
            }
        }

        public boolean contains(double value) {
            return value >= minimum && value <= maximum;
        }
    }

    public record Page(List<ArmoryEntry> entries, int total, int page, int pageCount, int pageSize) {
    }

    public enum Sort { NAME, ID, QUANTITY, ATTACHMENTS, DAMAGE, RPM, WEIGHT, RECENT }
    public enum Ownership { ANY, COLLECTED, MISSING }
    public enum Attachments { ANY, BARE, MODIFIED }
    public enum Toggle { ANY, YES, NO }
}
