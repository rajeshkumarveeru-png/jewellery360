import { FormEvent, useEffect, useState } from 'react';
import {
  domainPurities,
  createDomainPurity,
  updateDomainPurity,
  deleteDomainPurity,
} from '../api';
import { Empty, Table } from '../shared/ui';

type PurityRow = {
  id: number;
  name: string;
  karat?: string;
  fineness?: number | string;
  description?: string;
  active?: boolean;
};

type PurityForm = {
  name: string;
  karat: string;
  finenessPercent: string;
  description: string;
  active: boolean;
};

const emptyForm: PurityForm = {
  name: '',
  karat: '',
  finenessPercent: '',
  description: '',
  active: true,
};

const toPercent = (value: unknown): string => {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0) return '';
  return (n * 100).toFixed(3).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1');
};

const toFraction = (value: string): number => {
  const n = Number(value);
  return n / 100;
};

export default function PurityMaster({
  ready,
  onNotice,
}: {
  ready: boolean;
  onNotice: (message: string) => void;
}) {
  const [rows, setRows] = useState<PurityRow[]>([]);
  const [form, setForm] = useState<PurityForm>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  const load = async (): Promise<void> => {
    if (!ready) {
      setRows([]);
      return;
    }

    try {
      const response = await domainPurities();
      setRows(Array.isArray(response.data) ? response.data : []);
    } catch (error: any) {
      setRows([]);
      onNotice(error?.response?.data?.message || 'Unable to load purity master.');
    }
  };

  useEffect(() => {
    void load();
  }, [ready]);

  const reset = (): void => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const save = async (event: FormEvent): Promise<void> => {
    event.preventDefault();

    if (!ready) {
      onNotice('Select company and branch context first.');
      return;
    }

    const name = form.name.trim();
    const finenessPercent = Number(form.finenessPercent);

    if (!name) {
      onNotice('Purity name is required.');
      return;
    }

    if (!Number.isFinite(finenessPercent) || finenessPercent <= 0 || finenessPercent > 100) {
      onNotice('Fineness must be greater than 0 and up to 100%.');
      return;
    }

    setSaving(true);

    try {
      const payload = {
        name,
        karat: form.karat.trim() || null,
        // Database stores fineness as a fraction: 0.916 = 91.6%.
        fineness: toFraction(form.finenessPercent),
        description: form.description.trim() || null,
        active: form.active,
      };

      if (editingId !== null) {
        await updateDomainPurity(editingId, payload);
        onNotice(`${name} purity updated.`);
      } else {
        await createDomainPurity(payload);
        onNotice(`${name} purity created.`);
      }

      reset();
      await load();
    } catch (error: any) {
      onNotice(
        error?.response?.data?.message ||
        `Unable to ${editingId !== null ? 'update' : 'create'} purity.`
      );
    } finally {
      setSaving(false);
    }
  };

  const edit = (row: PurityRow): void => {
    setEditingId(row.id);
    setForm({
      name: row.name || '',
      karat: row.karat || '',
      finenessPercent: toPercent(row.fineness),
      description: row.description || '',
      active: row.active !== false,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const deactivate = async (row: PurityRow): Promise<void> => {
    if (!window.confirm(`Deactivate purity ${row.name}? Existing jewellery records will remain linked to it.`)) {
      return;
    }

    try {
      await deleteDomainPurity(row.id);
      onNotice(`${row.name} purity deactivated.`);
      if (editingId === row.id) reset();
      await load();
    } catch (error: any) {
      onNotice(error?.response?.data?.message || 'Unable to deactivate purity.');
    }
  };

  return (
    <section className="purityMasterPage">
      <div className="purityMasterTop">
        <div>
          <span className="eyebrow">GOLD & RATES · PURITY</span>
          <h2>Purity Master</h2>
          <p>
            Configure the purity standards available when jewellery items are created.
            Fineness is entered as a percentage and stored safely as a fraction.
          </p>
        </div>
        <div className="purityMasterSummary">
          <strong>{rows.length}</strong>
          <span>configured purities</span>
        </div>
      </div>

      <div className="purityMasterGrid">
        <div className="panel purityFormPanel">
          <div className="panelHead">
            <div>
              <span className="eyebrow">{editingId !== null ? 'EDIT PURITY' : 'NEW PURITY'}</span>
              <h2>{editingId !== null ? 'Edit purity' : 'Add purity'}</h2>
            </div>
            {editingId !== null && (
              <button type="button" className="ghost" onClick={reset}>
                Cancel edit
              </button>
            )}
          </div>

          <form className="formGrid" onSubmit={save}>
            <input
              required
              maxLength={50}
              placeholder="Purity name · e.g. 22K"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />

            <input
              maxLength={10}
              placeholder="Karat · e.g. 22K"
              value={form.karat}
              onChange={(e) => setForm({ ...form, karat: e.target.value })}
            />

            <input
              required
              type="number"
              min="0.001"
              max="100"
              step="0.001"
              placeholder="Fineness % · e.g. 91.6"
              value={form.finenessPercent}
              onChange={(e) => setForm({ ...form, finenessPercent: e.target.value })}
            />

            <textarea
              maxLength={500}
              placeholder="Description · e.g. 916 Gold"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />

            <label className="purityActiveToggle">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
              />
              <span>
                <b>Active purity</b>
                <small>Active purities appear in the Jewellery item dropdown.</small>
              </span>
            </label>

            <div className="buttonRow">
              <button className="primary" type="submit" disabled={saving}>
                {saving ? 'Saving…' : editingId !== null ? 'Update purity' : 'Add purity'}
              </button>
              {editingId !== null && (
                <button type="button" className="secondary" onClick={reset} disabled={saving}>
                  Clear
                </button>
              )}
            </div>
          </form>
        </div>

        <div className="panel purityListPanel">
          <div className="panelHead">
            <div>
              <span className="eyebrow">CONFIGURED STANDARDS</span>
              <h2>{rows.length} purity records</h2>
            </div>
            <button type="button" className="ghost" onClick={() => void load()}>
              Refresh
            </button>
          </div>

          <Table>
            <thead>
              <tr>
                <th>Purity</th>
                <th>Karat</th>
                <th>Fineness</th>
                <th>Description</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td><b>{row.name}</b></td>
                  <td>{row.karat || '—'}</td>
                  <td>{toPercent(row.fineness)}%</td>
                  <td>{row.description || '—'}</td>
                  <td>
                    <span className={`pill ${row.active === false ? '' : 'success'}`}>
                      {row.active === false ? 'INACTIVE' : 'ACTIVE'}
                    </span>
                  </td>
                  <td>
                    <div className="purityActions">
                      <button type="button" className="ghost small" onClick={() => edit(row)}>
                        Edit
                      </button>
                      {row.active !== false && (
                        <button type="button" className="ghost danger small" onClick={() => void deactivate(row)}>
                          Deactivate
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>

          {!rows.length && (
            <Empty text="No purity records yet. Add 24K, 22K, 20K, 18K or your own business-specific purity." />
          )}
        </div>
      </div>
    </section>
  );
}
