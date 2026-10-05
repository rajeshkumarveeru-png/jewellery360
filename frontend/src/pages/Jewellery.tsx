import { FormEvent, useEffect, useState } from 'react';
import BarcodeCameraScanner from './BarcodeCameraScanner';

import {
  domainCustomers,
  createDomainCustomer,
  domainCategories,
  createDomainCategory,
  domainDesigns,
  createDomainDesign,
  domainProducts,
  createDomainProduct,
  domainTags,
  createDomainTag,
  domainItems,
  createDomainItem,
  updateDomainItem,
  domainPurities,
  nextSku,
  nextTag,
} from '../api';

import { Table, Empty } from '../shared/ui';
import FormDrawer from '../shared/FormDrawer';
import BulkProductImport from './BulkProductImport';
import './Jewellery.css';

export default function JewelleryModule({
  ready,
  onNotice,
}: {
  ready: boolean;
  onNotice: (x: string) => void;
}) {
  const [tab, setTab] = useState<
    'CATEGORIES' | 'DESIGNS' | 'PRODUCTS' | 'TAGS' | 'ITEMS'
  >('ITEMS');

  const [categories, setCategories] = useState<any[]>([]);
  const [designs, setDesigns] = useState<any[]>([]);
  const [products, setProducts] = useState<any[]>([]);
  const [tags, setTags] = useState<any[]>([]);
  const [items, setItems] = useState<any[]>([]);
  const [purities, setPurities] = useState<any[]>([]);

  const [f, setF] = useState<any>({});

  const [simpleMode, setSimpleMode] = useState(true);
  const [masterMode, setMasterMode] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [bulkImportOpen, setBulkImportOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [itemEditOpen, setItemEditOpen] = useState(false);

  const load = async () => {
    if (!ready) return;

    try {
      const [
        categoriesResponse,
        designsResponse,
        productsResponse,
        tagsResponse,
        itemsResponse,
        puritiesResponse,
      ] = await Promise.all([
        domainCategories(),
        domainDesigns(),
        domainProducts(),
        domainTags(),
        domainItems(),
        domainPurities(),
      ]);

      setCategories(categoriesResponse.data);
      setDesigns(designsResponse.data);
      setProducts(productsResponse.data);
      setTags(tagsResponse.data);
      setItems(itemsResponse.data);
      setPurities(puritiesResponse.data);
    } catch (e: any) {
      onNotice(
        e?.response?.data?.message ||
          'Unable to load jewellery master data'
      );
    }
  };

  useEffect(() => {
    void load();
  }, [ready]);

  // Tenant-isolated auto SKU / tag number: the server numbers sequentially per company (01, 02, ... or PREFIX01).
  const fillSku = async () => {
    try {
      const r = await nextSku();
      setF((v: any) => ({ ...v, sku: r.data?.sku || v.sku }));
    } catch {
      /* the backend generates the SKU on save when the field is left blank */
    }
  };
  const fillTag = async () => {
    try {
      const r = await nextTag();
      setF((v: any) => ({ ...v, tagNo: r.data?.tagNo || v.tagNo }));
    } catch {
      /* the backend generates the tag number on save when the field is left blank */
    }
  };
  useEffect(() => {
    if (!ready) return;
    if (tab === 'PRODUCTS' && !f.sku) void fillSku();
    if (tab === 'TAGS' && !f.tagNo && !f.barcode) void fillTag();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, ready, f.sku, f.tagNo, f.barcode]);

  const save = async (e: FormEvent) => {
    e.preventDefault();

    if (!ready) {
      onNotice('Select company and branch context first.');
      return;
    }

    try {
      if (tab === 'CATEGORIES') {
        await createDomainCategory({
          name: f.name,
          code: f.code,
        });
      }

      if (tab === 'DESIGNS') {
        await createDomainDesign({
          name: f.name,
          code: f.code,
          description: f.description,
          categoryId: Number(f.categoryId),
        });
      }

      if (tab === 'PRODUCTS') {
        await createDomainProduct({
          name: f.name,
          sku: f.sku,
          description: f.description,
          categoryId: Number(f.categoryId),
          designId: f.designId
            ? Number(f.designId)
            : undefined,
        });
      }

      if (tab === 'TAGS') {
        let generatedTag = '';
        if (!String(f.barcode || f.tagNo || '').trim()) {
          generatedTag = String((await nextTag()).data?.tagNo || '');
        }

        const code = String(
          f.barcode || f.tagNo || generatedTag
        ).trim();

        const tagNo = String(
          f.tagNo || code
        ).trim();

        if (!code || !tagNo) {
          throw new Error(
            'Scan or enter a tag/barcode first.'
          );
        }

        await createDomainTag({
          tagNo,
          barcode: code,
          status: f.status || 'AVAILABLE',
        });
      }

      if (tab === 'ITEMS') {
        await createDomainItem({
          productId: Number(f.productId),
          tagId: Number(f.tagId),
          purityId: Number(f.purityId),
          grossWeight: Number(f.grossWeight || 0),
          stoneWeight: Number(f.stoneWeight || 0),
          netWeight: Number(
            f.netWeight ||
              Number(f.grossWeight || 0) -
                Number(f.stoneWeight || 0)
          ),
          huid: f.huid,
          wastagePercent: Number(
            f.wastagePercent || 0
          ),
          makingCharge: Number(
            f.makingCharge || 0
          ),
          stoneValue: Number(
            f.stoneValue || 0
          ),
          status: 'IN_STOCK',
        });
      }

      setF({});
      await load();

      onNotice(
        `${tab.toLowerCase()} created in PostgreSQL.`
      );
    } catch (e: any) {
      onNotice(
        e?.response?.data?.message ||
          `Unable to create ${tab.toLowerCase()}`
      );
    }
  };


  const openItemEdit = (item: any) => {
    if (!item) return;

    if (String(item.status || '').toUpperCase() !== 'IN_STOCK') {
      onNotice('Only available stock can be edited.');
      return;
    }

    setEditingItem(item);
    setF({
      productId: item.product?.id ?? '',
      tagId: item.tag?.id ?? '',
      purityId: item.purity?.id ?? '',
      grossWeight: item.grossWeight ?? '',
      stoneWeight: item.stoneWeight ?? '',
      netWeight: item.netWeight ?? '',
      huid: item.huid ?? '',
      wastagePercent: item.wastagePercent ?? '',
      makingCharge: item.makingCharge ?? '',
      stoneValue: item.stoneValue ?? '',
    });
    setItemEditOpen(true);
  };

  const updateItem = async () => {
    if (!editingItem?.id) {
      onNotice('No stock item selected.');
      return;
    }

    const grossWeight = Number(f.grossWeight || 0);
    const stoneWeight = Number(f.stoneWeight || 0);
    const netWeight =
      f.netWeight !== '' && f.netWeight != null
        ? Number(f.netWeight)
        : grossWeight - stoneWeight;

    if (!f.productId || !f.tagId || !f.purityId) {
      onNotice('Product, tag and purity are required.');
      return;
    }
    if (grossWeight <= 0) {
      onNotice('Gross weight must be greater than 0.');
      return;
    }
    if (stoneWeight < 0 || stoneWeight > grossWeight) {
      onNotice('Stone weight must be between 0 and gross weight.');
      return;
    }
    if (netWeight < 0 || netWeight > grossWeight) {
      onNotice('Net weight must be between 0 and gross weight.');
      return;
    }

    try {
      await updateDomainItem(Number(editingItem.id), {
        productId: Number(f.productId),
        tagId: Number(f.tagId),
        purityId: Number(f.purityId),
        grossWeight,
        stoneWeight,
        netWeight,
        huid: String(f.huid || '').trim() || null,
        wastagePercent: Number(f.wastagePercent || 0),
        makingCharge: Number(f.makingCharge || 0),
        stoneValue: Number(f.stoneValue || 0),
      });

      setItemEditOpen(false);
      setEditingItem(null);
      setF({});
      await load();
      onNotice('Jewellery stock updated successfully.');
    } catch (e: any) {
      onNotice(
        e?.response?.data?.message ||
        'Unable to update jewellery stock.'
      );
    }
  };

  const rows =
    tab === 'CATEGORIES'
      ? categories
      : tab === 'DESIGNS'
        ? designs
        : tab === 'PRODUCTS'
          ? products
          : tab === 'TAGS'
            ? tags
            : items;

  return (
    <>
      <div className="page-jewellery">

        {/* =====================================================
            TOP BAR
        ====================================================== */}
        <div className="jewelleryTopBar ultraGlassGrid">

          <div>
            <span className="eyebrow">
              JEWELLERY WORKSPACE
            </span>

            <h2>
              {masterMode
                ? 'Master data'
                : 'Quick add'}
            </h2>

            <small>
              {masterMode
                ? 'Manage categories, designs, products and tags when needed.'
                : 'Add a ready jewellery item in a few quick steps.'}
            </small>
          </div>

          <div className="jewelleryTopActions">

            {/* BULK IMPORT */}
            <button
              type="button"
              className="secondary"
              onClick={() => setBulkImportOpen(true)}
            >
              ⇧ Bulk import
            </button>

            {masterMode && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setMasterMode(false);
                  setTab('ITEMS');
                  setF({});
                }}
              >
                Quick add
              </button>
            )}

            <button
              type="button"
              className="primary"
              onClick={() => {
                setMasterMode((v) => !v);
                setTab('ITEMS');
                setF({});
              }}
            >
              {masterMode
                ? 'Hide setup'
                : 'Manage setup'}
            </button>

          </div>
        </div>

        {/* =====================================================
            MASTER TABS
        ====================================================== */}
        {masterMode && (
          <div className="reportTabs jewelleryMasterTabs">

            {(
              [
                'CATEGORIES',
                'DESIGNS',
                'PRODUCTS',
                'TAGS',
                'ITEMS',
              ] as const
            ).map((x) => (
              <button
                key={x}
                className={
                  tab === x ? 'active' : ''
                }
                onClick={() => {
                  setTab(x);
                  setF({});
                }}
              >
                {x}
              </button>
            ))}

          </div>
        )}

        {/* =====================================================
            MAIN CONTENT
        ====================================================== */}
        <div className="moduleGrid hasDrawer">

          {/* ===================================================
              CREATE / QUICK ADD PANEL
          ==================================================== */}
          <FormDrawer label="Add jewellery" title="Add jewellery" hint="Add products, tags and items here; they appear in the list below."><div className="panel jewelleryCreatePanel ultraGlassGrid">

            <span className="eyebrow">
              JEWELLERY
            </span>

            <h2>
              {tab === 'ITEMS'
                ? simpleMode
                  ? 'Add jewellery item'
                  : 'Add item details'
                : `Create ${tab.toLowerCase()}`}
            </h2>

            <small className="jewellerySimpleHint">
              {tab === 'ITEMS'
                ? simpleMode
                  ? 'Start with the four essentials. Add extra charges only when needed.'
                  : 'Complete the optional item details before saving.'
                : 'Keep master data clean and easy to maintain.'}
            </small>

            <form
              className="formGrid"
              onSubmit={save}
            >

              {/* ================= CATEGORIES ================= */}
              {tab === 'CATEGORIES' && (
                <>
                  <input
                    required
                    placeholder="Category name"
                    value={f.name || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        name: e.target.value,
                      })
                    }
                  />

                  <input
                    required
                    placeholder="Category code"
                    value={f.code || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        code: e.target.value,
                      })
                    }
                  />
                </>
              )}

              {/* ================= DESIGNS ================= */}
              {tab === 'DESIGNS' && (
                <>
                  <input
                    required
                    placeholder="Design name"
                    value={f.name || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        name: e.target.value,
                      })
                    }
                  />

                  <input
                    required
                    placeholder="Design code"
                    value={f.code || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        code: e.target.value,
                      })
                    }
                  />

                  <select
                    required
                    value={f.categoryId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        categoryId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Category
                    </option>

                    {categories.map((x) => (
                      <option
                        key={x.id}
                        value={x.id}
                      >
                        {x.name}
                      </option>
                    ))}
                  </select>

                  <textarea
                    placeholder="Description"
                    value={f.description || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        description:
                          e.target.value,
                      })
                    }
                  />
                </>
              )}

              {/* ================= PRODUCTS ================= */}
              {tab === 'PRODUCTS' && (
                <>
                  <input
                    required
                    placeholder="Product name"
                    value={f.name || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        name: e.target.value,
                      })
                    }
                  />

                  <div className="autoField">
                    <input
                      placeholder="SKU (auto-generated, editable)"
                      aria-label="SKU"
                      value={f.sku || ''}
                      onChange={(e) =>
                        setF({
                          ...f,
                          sku: e.target.value,
                        })
                      }
                    />
                    <button type="button" className="ghost autoButton" onClick={() => void fillSku()} title="Generate the next free SKU for your company">
                      Auto SKU
                    </button>
                  </div>

                  <select
                    required
                    value={f.categoryId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        categoryId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Category
                    </option>

                    {categories.map((x) => (
                      <option
                        key={x.id}
                        value={x.id}
                      >
                        {x.name}
                      </option>
                    ))}
                  </select>

                  <select
                    value={f.designId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        designId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Design (optional)
                    </option>

                    {designs.map((x) => (
                      <option
                        key={x.id}
                        value={x.id}
                      >
                        {x.name}
                      </option>
                    ))}
                  </select>

                  <textarea
                    placeholder="Description"
                    value={f.description || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        description:
                          e.target.value,
                      })
                    }
                  />
                </>
              )}

              {/* ================= TAGS ================= */}
              {tab === 'TAGS' && (
                <>
                  <div className="autoField">
                    <input
                      required
                      placeholder="Tag number (auto-generated, editable)"
                      aria-label="Tag number"
                      value={f.tagNo || ''}
                      onChange={(e) =>
                        setF({
                          ...f,
                          tagNo: e.target.value,
                        })
                      }
                    />
                    <button type="button" className="ghost autoButton" onClick={() => void fillTag()} title="Generate the next free tag number for your company">
                      Auto tag
                    </button>
                  </div>

                  <div className="smartScanField">

                    <input
                      required
                      placeholder="Scan or enter barcode"
                      value={f.barcode || ''}
                      onChange={(e) =>
                        setF({
                          ...f,
                          barcode:
                            e.target.value,
                        })
                      }
                    />

                    <button
                      type="button"
                      className="fieldScanButton"
                      aria-label="Scan barcode with camera"
                      title="Scan barcode"
                      onClick={() =>
                        setCameraOpen(true)
                      }
                    >
                      <span
                        className="scanFieldIcon"
                        aria-hidden="true"
                      >
                        ▦
                      </span>

                      <span className="scanFieldText">
                        Scan
                      </span>
                    </button>

                  </div>

                  <div className="scanFieldHint">
                    Scan the barcode to fill it
                    automatically, or type it
                    manually. Tag number stays
                    editable.
                  </div>

                  <select
                    value={
                      f.status || 'AVAILABLE'
                    }
                    onChange={(e) =>
                      setF({
                        ...f,
                        status:
                          e.target.value,
                      })
                    }
                  >
                    <option>
                      AVAILABLE
                    </option>

                    <option>
                      HOLD
                    </option>
                  </select>
                </>
              )}

              {/* ================= ITEMS ================= */}
              {tab === 'ITEMS' && (
                <>
                  <select
                    required
                    value={f.productId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        productId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Select product
                    </option>

                    {products.map((x) => (
                      <option
                        key={x.id}
                        value={x.id}
                      >
                        {x.name} · {x.sku}
                      </option>
                    ))}
                  </select>

                  <select
                    required
                    value={f.tagId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        tagId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Select tag
                    </option>

                    {tags.map((x) => (
                      <option
                        key={x.id}
                        value={x.id}
                      >
                        {x.tagNo} · {x.barcode}
                      </option>
                    ))}
                  </select>

                  <select
                    required
                    value={f.purityId || ''}
                    onChange={(e) =>
                      setF({
                        ...f,
                        purityId:
                          e.target.value,
                      })
                    }
                  >
                    <option value="">
                      Select purity
                    </option>

                    {purities
                      .filter(
                        (x) =>
                          x.active !== false
                      )
                      .map((x) => (
                        <option
                          key={x.id}
                          value={x.id}
                        >
                          {x.name}

                          {x.karat &&
                            x.karat !==
                              x.name
                            ? ` · ${x.karat}`
                            : ''}

                          {x.fineness != null
                            ? ` · ${(Number(
        x.fineness
    ) *
    100)
    .toFixed(3)
    .replace(
        /\.?0+$/,
        ''
    )}%`
                            : ''}
                        </option>
                      ))}
                  </select>

                  <input
                    required
                    type="number"
                    step="0.001"
                    placeholder="Gross weight (g)"
                    value={
                      f.grossWeight || ''
                    }
                    onChange={(e) =>
                      setF({
                        ...f,
                        grossWeight:
                          e.target.value,
                      })
                    }
                  />

                  {!simpleMode && (
                    <>
                      <input
                        type="number"
                        step="0.001"
                        placeholder="Stone weight (g)"
                        value={
                          f.stoneWeight || ''
                        }
                        onChange={(e) =>
                          setF({
                            ...f,
                            stoneWeight:
                              e.target.value,
                          })
                        }
                      />

                      <input
                        type="number"
                        step="0.001"
                        placeholder="Net weight (g)"
                        value={
                          f.netWeight || ''
                        }
                        onChange={(e) =>
                          setF({
                            ...f,
                            netWeight:
                              e.target.value,
                          })
                        }
                      />

                      <input
                        placeholder="HUID"
                        value={f.huid || ''}
                        onChange={(e) =>
                          setF({
                            ...f,
                            huid:
                              e.target.value,
                          })
                        }
                      />

                      <input
                        type="number"
                        step="0.001"
                        placeholder="Wastage %"
                        value={
                          f.wastagePercent ||
                          ''
                        }
                        onChange={(e) =>
                          setF({
                            ...f,
                            wastagePercent:
                              e.target.value,
                          })
                        }
                      />

                      <input
                        type="number"
                        step="0.001"
                        placeholder="Making charge"
                        value={
                          f.makingCharge || ''
                        }
                        onChange={(e) =>
                          setF({
                            ...f,
                            makingCharge:
                              e.target.value,
                          })
                        }
                      />
                    </>
                  )}

                  <button
                    type="button"
                    className="modeToggle jewelleryModeToggle"
                    onClick={() =>
                      setSimpleMode(
                        (v) => !v
                      )
                    }
                  >
                    {simpleMode
                      ? 'Add details'
                      : 'Quick add'}
                  </button>
                </>
              )}

              {/* CREATE BUTTON */}
              <button
                type="submit"
                className="primary"
              >
                Create {tab.toLowerCase()}
              </button>

            </form>

            {/* BARCODE CAMERA */}
            <BarcodeCameraScanner
              open={cameraOpen}
              onClose={() =>
                setCameraOpen(false)
              }
              onDetected={(value) => {
                const code = String(
                  value || ''
                ).trim();

                if (!code) return;

                const existing =
                  tags.find(
                    (x) =>
                      String(
                        x.barcode || ''
                      ).trim() === code ||
                      String(
                        x.tagNo || ''
                      ).trim() === code
                  );

                setF((v: any) => ({
                  ...v,
                  barcode:
                    existing?.barcode ||
                    code,
                  tagNo:
                    existing?.tagNo ||
                    code,
                  status:
                    existing?.status ||
                    v.status ||
                    'AVAILABLE',
                }));

                setCameraOpen(false);

                onNotice(
                  existing
                    ? `Tag ${existing.tagNo} found.`
                    : `Scanned code ${code} filled automatically.`
                );
              }}
            />

          </div>
</FormDrawer>

          {/* ===================================================
              LIST PANEL
          ==================================================== */}
          <div className="panel ultraGlassGrid">

            <div className="panelHead">

              <h2>
                {rows.length}{' '}
                {tab.toLowerCase()}
              </h2>

              <button
                type="button"
                className="ghost"
                onClick={() => void load()}
              >
                Refresh
              </button>

            </div>

            <Table>

              <thead>
                <tr>

                  {tab === 'CATEGORIES' ? (
                    <>
                      <th>Name</th>
                      <th>Code</th>
                    </>
                  ) : tab === 'DESIGNS' ? (
                    <>
                      <th>Name</th>
                      <th>Code</th>
                      <th>Category</th>
                    </>
                  ) : tab === 'PRODUCTS' ? (
                    <>
                      <th>Name</th>
                      <th>SKU</th>
                      <th>Category</th>
                    </>
                  ) : tab === 'TAGS' ? (
                    <>
                      <th>Tag</th>
                      <th>Barcode</th>
                      <th>Status</th>
                    </>
                  ) : (
                    <>
                      <th>Product</th>
                      <th>Tag</th>
                      <th>Purity</th>
                      <th>Net weight</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </>
                  )}

                </tr>
              </thead>

              <tbody>

                {rows.map((x) => (
                  <tr key={x.id}>

                    {tab === 'CATEGORIES' ? (
                      <>
                        <td>
                          {x.name}
                        </td>

                        <td>
                          {x.code}
                        </td>
                      </>
                    ) : tab === 'DESIGNS' ? (
                      <>
                        <td>
                          {x.name}
                        </td>

                        <td>
                          {x.code}
                        </td>

                        <td>
                          {x.category?.name ||
                            '—'}
                        </td>
                      </>
                    ) : tab === 'PRODUCTS' ? (
                      <>
                        <td>
                          {x.name}
                        </td>

                        <td>
                          {x.sku}
                        </td>

                        <td>
                          {x.category?.name ||
                            '—'}
                        </td>
                      </>
                    ) : tab === 'TAGS' ? (
                      <>
                        <td>
                          {x.tagNo}
                        </td>

                        <td>
                          {x.barcode}
                        </td>

                        <td>
                          {x.status}
                        </td>
                      </>
                    ) : (
                      <>
                        <td>
                          {x.product?.name ||
                            '—'}
                        </td>

                        <td>
                          {x.tag?.tagNo ||
                            '—'}
                        </td>

                        <td>
                          {x.purity?.name ||
                            '—'}
                        </td>

                        <td>
                          {x.netWeight}
                        </td>

                        <td>
                          <span
                            className={
                              String(x.status || '').toUpperCase() === 'IN_STOCK'
                                ? 'pill success'
                                : 'pill'
                            }
                          >
                            {x.status}
                          </span>
                        </td>

                        <td>
                          {String(x.status || '').toUpperCase() === 'IN_STOCK' && (
                            <button
                              type="button"
                              className="tableEditButton"
                              onClick={() => openItemEdit(x)}
                            >
                              ✎ Edit
                            </button>
                          )}
                        </td>
                      </>
                    )}

                  </tr>
                ))}

              </tbody>

            </Table>

            {!rows.length && (
              <Empty
                text={`No ${tab.toLowerCase()} yet.`}
              />
            )}

          </div>

        </div>

      </div>


      {/* =======================================================
          EDIT AVAILABLE STOCK ITEM
      ======================================================== */}
      {itemEditOpen && editingItem && (
        <div className="stockEditOverlay">
          <div className="stockEditModal">
            <div className="stockEditHeader">
              <div>
                <span className="eyebrow">INVENTORY</span>
                <h2>Edit Jewellery Stock</h2>
                <small>
                  Update available stock before it is sold.
                  Tag: {editingItem.tag?.tagNo || '—'}
                </small>
              </div>

              <button
                type="button"
                className="ghost"
                onClick={() => {
                  setItemEditOpen(false);
                  setEditingItem(null);
                  setF({});
                }}
              >
                Close
              </button>
            </div>

            <div className="stockEditGrid">
              <label>
                <span>Product</span>
                <select
                  value={f.productId || ''}
                  onChange={(e) =>
                    setF({ ...f, productId: e.target.value })
                  }
                >
                  <option value="">Select product</option>
                  {products.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.name} · {x.sku}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>Tag</span>
                <select
                  value={f.tagId || ''}
                  onChange={(e) =>
                    setF({ ...f, tagId: e.target.value })
                  }
                >
                  <option value="">Select tag</option>
                  {tags.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.tagNo} · {x.barcode}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>Purity</span>
                <select
                  value={f.purityId || ''}
                  onChange={(e) =>
                    setF({ ...f, purityId: e.target.value })
                  }
                >
                  <option value="">Select purity</option>
                  {purities
                    .filter((x) => x.active !== false)
                    .map((x) => (
                      <option key={x.id} value={x.id}>
                        {x.name}
                        {x.karat && x.karat !== x.name
                          ? ` · ${x.karat}`
                          : ''}
                      </option>
                    ))}
                </select>
              </label>

              <label>
                <span>Gross Weight (g)</span>
                <input
                  type="number"
                  step="0.001"
                  value={f.grossWeight ?? ''}
                  onChange={(e) =>
                    setF({ ...f, grossWeight: e.target.value })
                  }
                />
              </label>

              <label>
                <span>Stone Weight (g)</span>
                <input
                  type="number"
                  step="0.001"
                  value={f.stoneWeight ?? ''}
                  onChange={(e) =>
                    setF({ ...f, stoneWeight: e.target.value })
                  }
                />
              </label>

              <label>
                <span>Net Weight (g)</span>
                <input
                  type="number"
                  step="0.001"
                  value={f.netWeight ?? ''}
                  onChange={(e) =>
                    setF({ ...f, netWeight: e.target.value })
                  }
                />
              </label>

              <label>
                <span>HUID</span>
                <input
                  value={f.huid || ''}
                  onChange={(e) =>
                    setF({ ...f, huid: e.target.value })
                  }
                />
              </label>

              <label>
                <span>Wastage %</span>
                <input
                  type="number"
                  step="0.001"
                  value={f.wastagePercent ?? ''}
                  onChange={(e) =>
                    setF({ ...f, wastagePercent: e.target.value })
                  }
                />
              </label>

              <label>
                <span>Making Charge</span>
                <input
                  type="number"
                  step="0.01"
                  value={f.makingCharge ?? ''}
                  onChange={(e) =>
                    setF({ ...f, makingCharge: e.target.value })
                  }
                />
              </label>

              <label>
                <span>Stone Value</span>
                <input
                  type="number"
                  step="0.01"
                  value={f.stoneValue ?? ''}
                  onChange={(e) =>
                    setF({ ...f, stoneValue: e.target.value })
                  }
                />
              </label>
            </div>

            <div className="stockEditFooter">
              <button
                type="button"
                className="ghost"
                onClick={() => {
                  setItemEditOpen(false);
                  setEditingItem(null);
                  setF({});
                }}
              >
                Cancel
              </button>

              <button
                type="button"
                className="primary"
                onClick={() => void updateItem()}
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =======================================================
          BULK PRODUCT IMPORT MODAL
      ======================================================== */}
      {bulkImportOpen && (
        <BulkProductImport
          onClose={() => {
            setBulkImportOpen(false);
            void load();
          }}
          onNotice={onNotice}
        />
      )}

    </>
  );
}
