import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  domainGoldRates,
  domainPurities,
  createDomainGoldRate,
  headerGoldRates,
} from '../api';
import { Empty, Table } from '../shared/ui';
import FormDrawer from '../shared/FormDrawer';
import './GoldRates.css';
import PurityMaster from './PurityMaster';

type GoldRateRow = {
  karat: string;
  ratePerGram: number;
  [key: string]: unknown;
};

type PurityRow = {
  id: number | string;
  name?: string;
  karat?: string;
  [key: string]: unknown;
};

type SavedGoldRate = {
  id: number | string;
  purity?: {
    id?: number | string;
    name?: string;
    karat?: string;
  };
  purityId?: number | string;
  karat?: string;
  ratePerGram?: number | string;
  rateDate?: string;
  source?: string;
  active?: boolean;
  [key: string]: unknown;
};

type MarketResponse = {
  marketRates?: unknown[];
  rates?: unknown[];
  date?: string;
  rateDate?: string;
  marketSource?: string;
  source?: string;
  data?: {
    marketRates?: unknown[];
    rates?: unknown[];
    date?: string;
    rateDate?: string;
    marketSource?: string;
    source?: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
};

type GoldRateForm = {
  rateDate: string;
  rates: Record<string, string | number>;
};

const localToday = (): string => {
  const d = new Date();

  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');

  return `${y}-${m}-${day}`;
};

const toKarat = (value: unknown): string => {
  const raw = String(value ?? '').toUpperCase().trim();

  const match = raw.match(/(24|22|18|14)/);

  return match ? `${match[1]}K` : raw;
};

const normalizeRateRows = (value: unknown): GoldRateRow[] => {
  const input = value as MarketResponse | unknown[] | null | undefined;

  let source: unknown[] = [];

  if (Array.isArray(input)) {
    source = input;
  } else if (input && typeof input === 'object') {
    const obj = input as MarketResponse;

    if (Array.isArray(obj.marketRates)) {
      source = obj.marketRates;
    } else if (Array.isArray(obj.rates)) {
      source = obj.rates;
    } else if (Array.isArray(obj.data?.marketRates)) {
      source = obj.data.marketRates;
    } else if (Array.isArray(obj.data?.rates)) {
      source = obj.data.rates;
    }
  }

  return source
      .map((item: unknown): GoldRateRow => {
        const x =
            item !== null && typeof item === 'object'
                ? (item as Record<string, unknown>)
                : {};

        return {
          ...x,

          karat: toKarat(
              x.karat ??
              x.purity ??
              x.goldKarat ??
              x.name ??
              x.purityName
          ),

          ratePerGram: Number(
              x.ratePerGram ??
              x.rate ??
              x.pricePerGram ??
              x.price ??
              x.value ??
              0
          ),
        };
      })
      .filter(
          (x: GoldRateRow): boolean =>
              Boolean(x.karat) && x.ratePerGram > 0
      );
};

const extractDate = (value: unknown): string => {
  if (!value || typeof value !== 'object') {
    return '';
  }

  const obj = value as MarketResponse;

  return (
      obj.date ||
      obj.rateDate ||
      obj.data?.date ||
      obj.data?.rateDate ||
      ''
  );
};

const extractSource = (value: unknown): string => {
  if (!value || typeof value !== 'object') {
    return 'GoodReturns - Cuddalore';
  }

  const obj = value as MarketResponse;

  return (
      obj.marketSource ||
      obj.source ||
      obj.data?.marketSource ||
      obj.data?.source ||
      'GoodReturns - Cuddalore'
  );
};

const normalizeSavedRows = (value: unknown): SavedGoldRate[] => {
  if (!Array.isArray(value)) {
    return [];
  }

  return value as SavedGoldRate[];
};

const getSavedRateDate = (row: SavedGoldRate): string => {
  return String(row.rateDate ?? '');
};

const getSavedKarat = (row: SavedGoldRate): string => {
  return toKarat(
      row.purity?.karat ??
      row.purity?.name ??
      row.karat
  );
};

const getSavedRate = (row: SavedGoldRate): number => {
  return Number(row.ratePerGram ?? 0);
};

export default function GoldRatesModule({
                                          ready,
                                          onNotice,
                                        }: {
  ready: boolean;
  onNotice: (message: string) => void;
}) {
  const [sectionTab, setSectionTab] = useState<'RATES' | 'PURITY'>('RATES');
  const [rows, setRows] = useState<SavedGoldRate[]>([]);
  const [purities, setPurities] = useState<PurityRow[]>([]);

  const [market, setMarket] = useState<MarketResponse>({
    marketRates: [],
  });

  const [marketError, setMarketError] = useState<boolean>(false);

  const [f, setF] = useState<GoldRateForm>({
    rateDate: localToday(),
    rates: {},
  });

  const load = async (): Promise<void> => {
    if (!ready) {
      setRows([]);
      setPurities([]);
      setMarket({
        marketRates: [],
        date: localToday(),
        source: 'GoodReturns - Cuddalore',
      });
      setMarketError(false);
      return;
    }

    setMarketError(false);

    const [
      ratesResult,
      puritiesResult,
      marketResult,
    ] = await Promise.allSettled([
      domainGoldRates(),
      domainPurities(),
      headerGoldRates(),
    ]);

    // ---------------------------------------------------------
    // Saved PostgreSQL gold rates
    // ---------------------------------------------------------

    let saved: SavedGoldRate[] = [];

    if (ratesResult.status === 'fulfilled') {
      saved = normalizeSavedRows(ratesResult.value.data);
      setRows(saved);
    } else {
      setRows([]);
    }

    // ---------------------------------------------------------
    // Purities
    // ---------------------------------------------------------

    if (puritiesResult.status === 'fulfilled') {
      const purityData = Array.isArray(
          puritiesResult.value.data
      )
          ? (puritiesResult.value.data as PurityRow[])
          : [];

      setPurities(purityData);
    } else {
      setPurities([]);
    }

    // ---------------------------------------------------------
    // Live market rate
    // ---------------------------------------------------------

    let live: GoldRateRow[] = [];

    if (marketResult.status === 'fulfilled') {
      const raw = marketResult.value.data as unknown;

      live = normalizeRateRows(raw);

      setMarket({
        ...(raw && typeof raw === 'object'
            ? (raw as MarketResponse)
            : {}),

        marketRates: live,

        date:
            extractDate(raw) ||
            localToday(),

        source:
            extractSource(raw),
      });

      if (!live.length) {
        setMarketError(true);
      }
    } else {
      setMarket({
        marketRates: [],
        date: localToday(),
        source: 'GoodReturns - Cuddalore',
      });

      setMarketError(true);
    }

    // ---------------------------------------------------------
    // PostgreSQL fallback
    //
    // If the live market feed is unavailable but we have saved
    // rates, show the latest saved rate clearly as fallback.
    // ---------------------------------------------------------

    if (!live.length && saved.length > 0) {
      const latestByKarat: Record<string, GoldRateRow> = {};

      const sortedSaved = [...saved].sort(
          (
              a: SavedGoldRate,
              b: SavedGoldRate
          ): number =>
              getSavedRateDate(b).localeCompare(
                  getSavedRateDate(a)
              )
      );

      sortedSaved.forEach(
          (row: SavedGoldRate): void => {
            const karat = getSavedKarat(row);
            const rate = getSavedRate(row);

            if (
                karat &&
                rate > 0 &&
                !latestByKarat[karat]
            ) {
              latestByKarat[karat] = {
                karat,
                ratePerGram: rate,
              };
            }
          }
      );

      const fallback: GoldRateRow[] =
          Object.values(latestByKarat);

      if (fallback.length > 0) {
        const latestSavedDate =
            sortedSaved[0]?.rateDate ||
            localToday();

        setMarket({
          marketRates: fallback,
          date: String(latestSavedDate),
          source: 'Saved PostgreSQL GoldRate',
        });

        // We have data to display, even though the live
        // market service is unavailable.
        setMarketError(false);
      }
    }

    // ---------------------------------------------------------
    // Populate form ONLY from live market data
    // ---------------------------------------------------------

    if (live.length > 0) {
      const next: Record<string, number> = {};

      live.forEach(
          (row: GoldRateRow): void => {
            if (row.karat) {
              next[row.karat] = row.ratePerGram;
            }
          }
      );

      setF(
          (
              previous: GoldRateForm
          ): GoldRateForm => ({
            ...previous,

            rates: {
              ...next,
              ...previous.rates,
            },

            rateDate:
                extractDate(
                    marketResult.status === 'fulfilled'
                        ? marketResult.value.data
                        : undefined
                ) || previous.rateDate,
          })
      );
    }
  };

  useEffect(() => {
    void load();
  }, [ready]);

  // -----------------------------------------------------------
  // Purity lookup
  // -----------------------------------------------------------

  const purityByKarat = useMemo(
      (): Record<string, PurityRow> => {
        const result: Record<string, PurityRow> = {};

        purities.forEach(
            (purity: PurityRow): void => {
              const karat = toKarat(
                  purity.karat ||
                  purity.name
              );

              if (karat) {
                result[karat] = purity;
              }
            }
        );

        return result;
      },
      [purities]
  );

  // -----------------------------------------------------------
  // Current market rates
  // -----------------------------------------------------------

  const marketRates: GoldRateRow[] =
      normalizeRateRows(market);

  const liveMarket: boolean =
      marketRates.length > 0 &&
      !String(
          market.source || ''
      ).includes(
          'Saved PostgreSQL'
      );

  // -----------------------------------------------------------
  // Save manual gold rates
  // -----------------------------------------------------------

  const save = async (
      e: FormEvent
  ): Promise<void> => {
    e.preventDefault();

    if (!ready) {
      onNotice(
          'Select company and branch context first.'
      );
      return;
    }

    try {
      const entries = Object.entries(
          f.rates || {}
      ).filter(
          (
              [, value]: [
                string,
                    string | number
              ]
          ): boolean =>
              Number(value) > 0
      );

      if (!entries.length) {
        onNotice(
            'Enter at least one gold rate.'
        );
        return;
      }

      for (
          const [
            karat,
            value,
          ] of entries
          ) {
        const purity =
            purityByKarat[
                toKarat(karat)
                ];

        if (!purity) {
          onNotice(
              `Purity ${karat} is not configured.`
          );
          return;
        }

        await createDomainGoldRate({
          purityId: purity.id,
          rateDate: f.rateDate,
          ratePerGram: Number(value),
          source:
              'Manual / Jewellery360',
          active: true,
        });
      }

      onNotice(
          'Gold rates saved to PostgreSQL.'
      );

      await load();
    } catch (error: unknown) {
      const message =
          error &&
          typeof error === 'object' &&
          'response' in error
              ? (
                  error as {
                    response?: {
                      data?: {
                        message?: string;
                      };
                    };
                  }
              ).response?.data?.message
              : undefined;

      onNotice(
          message ||
          'Unable to save gold rates'
      );
    }
  };

  // -----------------------------------------------------------
  // Use current live market rate
  // -----------------------------------------------------------

  const useMarket = (): void => {
    if (!liveMarket) {
      onNotice(
          'Live Cuddalore market rate is not available right now.'
      );
      return;
    }

    const next: Record<string, number> = {};

    marketRates.forEach(
        (row: GoldRateRow): void => {
          if (row.karat) {
            next[row.karat] =
                row.ratePerGram;
          }
        }
    );

    setF(
        (
            previous: GoldRateForm
        ): GoldRateForm => ({
          ...previous,

          rates: {
            ...previous.rates,
            ...next,
          },

          rateDate:
              market.date ||
              localToday(),
        })
    );

    onNotice(
        'Current Cuddalore market rates loaded into the form.'
    );
  };

  return (
      <section className="menuPage menu-gold-rates-rates">
        <div className="goldRatesSectionTabs" role="tablist" aria-label="Gold and rates sections">
          <button
              type="button"
              className={sectionTab === 'RATES' ? 'active' : ''}
              onClick={() => setSectionTab('RATES')}
          >
            Gold Rates
          </button>
          <button
              type="button"
              className={sectionTab === 'PURITY' ? 'active' : ''}
              onClick={() => setSectionTab('PURITY')}
          >
            Purity Master
          </button>
        </div>

        {sectionTab === 'PURITY' ? (
          <PurityMaster ready={ready} onNotice={onNotice} />
        ) : (
          <>
            {/* =====================================================
              TOP GRID
          ===================================================== */}

            <div className="goldRateTopGrid hasDrawer">

          {/* ===================================================
            SAVE GOLD RATES
        =================================================== */}

          <FormDrawer needsManage label="Save gold rates" title="Save gold rates" hint="Rates are saved per purity and used on every new bill."><div className="panel goldRateFormPanel">

            <div className="panelHead">

              <div>
              <span className="eyebrow">
                GOLD & RATES
              </span>

                <h2>
                  Save Gold Rates
                </h2>
              </div>

              <span className="pill success">
              LIVE DATABASE
            </span>

            </div>

            <form
                className="formGrid"
                onSubmit={save}
            >

              {/* Date */}

              <input
                  type="date"
                  required
                  value={f.rateDate}
                  onChange={(
                      e
                  ) =>
                      setF(
                          (
                              previous: GoldRateForm
                          ): GoldRateForm => ({
                            ...previous,
                            rateDate:
                            e.target.value,
                          })
                      )
                  }
              />

              {/* Gold rates */}

              {(purities
                  .filter((purity: PurityRow): boolean => purity.active !== false)
                  .map((purity: PurityRow): string => toKarat(purity.karat || purity.name))
                  .filter(Boolean)
                  .length
                  ? purities
                      .filter((purity: PurityRow): boolean => purity.active !== false)
                      .map((purity: PurityRow): string => toKarat(purity.karat || purity.name))
                      .filter(Boolean)
                  : ['24K', '22K', '20K', '18K', '14K']
              ).map(
                  (
                      karat: string
                  ) => (
                      <input
                          key={karat}
                          type="number"
                          step="0.001"
                          placeholder={`${karat} rate / g`}
                          value={
                              f.rates?.[karat] ??
                              ''
                          }
                          onChange={(
                              e
                          ) =>
                              setF(
                                  (
                                      previous: GoldRateForm
                                  ): GoldRateForm => ({
                                    ...previous,

                                    rates: {
                                      ...previous.rates,
                                      [karat]:
                                      e.target.value,
                                    },
                                  })
                              )
                          }
                      />
                  )
              )}

              {/* Actions */}

              <div className="buttonRow">

                <button
                    type="button"
                    className="secondary"
                    onClick={useMarket}
                    disabled={!liveMarket}
                >
                  Use current market rate
                </button>

                <button
                    type="submit"
                    className="primary"
                >
                  Save Gold Rates
                </button>

              </div>

            </form>
          </div>
</FormDrawer>

          {/* ===================================================
            MARKET RATE
        =================================================== */}

          <div className="panel marketRatePanel">

            <div className="panelHead">

              <div>
              <span className="eyebrow">
                MARKET REFERENCE
              </span>

                <h2>
                  Today's Cuddalore rate
                </h2>
              </div>

              <div className="marketHeadActions">

              <span className="pill">
                {market.date ||
                    localToday()}
              </span>

                <button
                    type="button"
                    className="ghost"
                    onClick={() => void load()}
                >
                  Refresh
                </button>

              </div>

            </div>

            {marketRates.length > 0 ? (
                <>
                  <div className="marketRateCards">

                    {marketRates.map(
                        (
                            row: GoldRateRow
                        ) => (
                            <div
                                className="marketRateCard"
                                key={row.karat}
                            >

                      <span>
                        {row.karat}
                      </span>

                              <strong>
                                ₹
                                {Number(
                                    row.ratePerGram
                                ).toLocaleString(
                                    'en-IN',
                                    {
                                      maximumFractionDigits: 2,
                                    }
                                )}
                              </strong>

                              <small>
                                per gram
                              </small>

                            </div>
                        )
                    )}

                  </div>

                  <div
                      className={`marketAvailability ${
                          liveMarket
                              ? 'live'
                              : 'fallback'
                      }`}
                  >
                    {liveMarket
                        ? '● Live market reference'
                        : '● Latest saved PostgreSQL rate — market feed unavailable'}
                  </div>
                </>
            ) : (
                <Empty
                    text={
                      marketError
                          ? 'Cuddalore market rate could not be fetched from the configured market service. Check the backend market-rate endpoint / external source.'
                          : 'Market rate temporarily unavailable.'
                    }
                />
            )}

            <small className="marketSource">
              Indicative reference only ·{' '}
              {market.source ||
                  'GoodReturns - Cuddalore'}
            </small>

          </div>
        </div>

        {/* =====================================================
          SAVED POSTGRESQL RATES
      ===================================================== */}

        <div className="panel savedRatesPanel">

          <div className="panelHead">

            <div>
            <span className="eyebrow">
              POSTGRESQL
            </span>

              <h2>
                {rows.length} saved rate records
              </h2>
            </div>

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
              <th>
                Purity
              </th>

              <th>
                Rate / g
              </th>

              <th>
                Rate / 10g
              </th>

              <th>
                Date
              </th>

              <th>
                Source
              </th>

              <th>
                Status
              </th>
            </tr>
            </thead>

            <tbody>

            {rows.map(
                (
                    row: SavedGoldRate
                ) => {

                  const rate =
                      getSavedRate(row);

                  return (
                      <tr
                          key={row.id}
                      >

                        <td>
                          <b>
                            {row.purity?.name ||
                                row.purity?.karat ||
                                row.karat ||
                                '—'}
                          </b>
                        </td>

                        <td>
                          ₹
                          {rate.toLocaleString(
                              'en-IN',
                              {
                                maximumFractionDigits: 3,
                              }
                          )}
                        </td>

                        <td>
                          ₹
                          {(
                              rate * 10
                          ).toLocaleString(
                              'en-IN',
                              {
                                maximumFractionDigits: 3,
                              }
                          )}
                        </td>

                        <td>
                          {row.rateDate ||
                              '—'}
                        </td>

                        <td>
                          {row.source ||
                              '—'}
                        </td>

                        <td>
                      <span className="pill success">
                        {row.active
                            ? 'ACTIVE'
                            : 'INACTIVE'}
                      </span>
                        </td>

                      </tr>
                  );
                }
            )}

            </tbody>

          </Table>

          {!rows.length && (
              <Empty
                  text="No saved gold-rate records yet. Save a rate above to create a real PostgreSQL GoldRate record."
              />
          )}

            </div>
          </>
        )}
      </section>
  );
}