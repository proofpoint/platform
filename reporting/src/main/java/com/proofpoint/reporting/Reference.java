package com.proofpoint.reporting;

import static java.util.Objects.requireNonNull;

class Reference
{
    private final Object referent;

    Reference(Object referent) {
        this.referent = requireNonNull(referent);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Reference reference = (Reference) o;

        return referent == reference.referent;
    }

    @Override
    public int hashCode()
    {
        return referent.hashCode();
    }
}
