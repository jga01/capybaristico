import React, { useState, useEffect, createContext, useContext } from 'react';
import { produce } from 'immer';
import { getEffectOptions } from '../../../services/adminApiService';
import './EffectBuilder.css';

const EffectOptionsContext = createContext(null);

const useEffectOptions = () => useContext(EffectOptionsContext);

const defaultEffect = () => ({
    trigger: "ON_PLAY",
    condition: null,
    action: "DEAL_DAMAGE",
    params: { targets: "SELF", amount: 1 }
});

const defaultCondition = () => ({
    type: "SELF_HAS_FLAG",
    params: { flagName: "" }
});

const OptionSelector = ({ value, onChange, options, placeholder = "Select..." }) => (
    <select value={value || ''} onChange={onChange}>
        <option value="" disabled>{placeholder}</option>
        {options?.map(opt => <option key={opt} value={opt}>{opt}</option>)}
    </select>
);

const ValueInput = ({ value, onChange, label }) => {
    const { valueSourceTypes, statTypes, targetSelectors } = useEffectOptions();
    const isDynamic = typeof value === 'object' && value !== null;

    const handleTypeChange = (e) => {
        const isChecked = e.target.checked;
        if (isChecked) {
            onChange({ source: "STAT", statName: "ATK", cardContext: "SELF" });
        } else {
            onChange(0);
        }
    };

    const handleDynamicChange = (field, fieldValue) => {
        onChange(produce(value, draft => {
            draft[field] = fieldValue;
        }));
    };

    return (
        <div className="value-input">
            <label>{label}</label>
            <div className="value-input-type-toggle">
                <label><input type="checkbox" checked={!isDynamic} onChange={() => onChange(0)} /> Static</label>
                <label><input type="checkbox" checked={isDynamic} onChange={() => onChange({ source: "STAT", statName: "ATK", cardContext: "SELF" })} /> Dynamic</label>
            </div>

            {!isDynamic && <input type="number" value={value || 0} onChange={(e) => onChange(parseInt(e.target.value, 10))} />}

            {isDynamic && (
                <div className="dynamic-value-builder">
                    <OptionSelector value={value.source} options={valueSourceTypes} onChange={(e) => handleDynamicChange('source', e.target.value)} />
                    {value.source === 'STAT' && (
                        <>
                            <OptionSelector value={value.statName} options={statTypes} onChange={(e) => handleDynamicChange('statName', e.target.value)} />
                            <OptionSelector value={value.cardContext} options={["SELF", "EVENT_TARGET", "EVENT_SOURCE"]} onChange={(e) => handleDynamicChange('cardContext', e.target.value)} />
                            <div className="flex-row"><label>Multiplier:</label><input type="number" step="0.1" value={value.multiplier || 1} onChange={e => handleDynamicChange('multiplier', parseFloat(e.target.value))} /></div>
                        </>
                    )}
                    {value.source === 'DYNAMIC_COUNT' && (
                        <>
                            <OptionSelector value={value.countType} options={["FRIENDLY_CARDS_WITH_TYPE", "OTHER_FRIENDLY_CARDS", "HIGHEST_LIFE_ON_FIELD_EXCLUDING_SELF"]} onChange={(e) => handleDynamicChange('countType', e.target.value)} />
                            {value.countType === "FRIENDLY_CARDS_WITH_TYPE" && <div className="flex-row"><label>Type Name:</label><input type="text" value={value.typeName || ''} onChange={e => handleDynamicChange('typeName', e.target.value)} /></div>}
                        </>
                    )}
                    {value.source === 'EVENT_DATA' && <div className="flex-row"><label>Key:</label><input type="text" value={value.key || ''} onChange={e => handleDynamicChange('key', e.target.value)} /></div>}
                    {value.source === 'FLAG_VALUE' && <div className="flex-row"><label>Flag Name:</label><input type="text" value={value.flagName || ''} onChange={e => handleDynamicChange('flagName', e.target.value)} /></div>}
                </div>
            )}
        </div>
    );
};


const ConditionBuilder = ({ condition, onChange, onRemove }) => {
    const { conditionTypes } = useEffectOptions();

    const handleTypeChange = (e) => {
        const newType = e.target.value;
        if (newType === 'ALL_OF' || newType === 'ANY_OF') {
            onChange({ type: newType, conditions: [] });
        } else {
            onChange({ type: newType, params: {} });
        }
    };

    const handleParamChange = (param, value) => {
        onChange(produce(condition, draft => {
            if (!draft.params) draft.params = {};
            draft.params[param] = value;
        }));
    };

    const handleAddSubCondition = () => {
        onChange(produce(condition, draft => {
            if (!draft.conditions) draft.conditions = [];
            draft.conditions.push(defaultCondition());
        }));
    };

    const handleSubConditionChange = (index, newSubCondition) => {
        onChange(produce(condition, draft => {
            draft.conditions[index] = newSubCondition;
        }));
    };

    const handleRemoveSubCondition = (index) => {
        onChange(produce(condition, draft => {
            draft.conditions.splice(index, 1);
        }));
    };

    const renderParams = () => {
        switch (condition.type) {
            case 'SELF_HAS_FLAG':
            case 'SOURCE_HAS_CARD_ID':
            case 'FRIENDLY_CARD_IN_PLAY':
            case 'ENEMY_CARD_IN_PLAY':
                return (
                    <>
                        <div className="flex-row"><label>{condition.type.includes('CARD_ID') ? "Card ID" : "Flag Name"}:</label><input type="text" value={condition.params?.flagName || condition.params?.cardId || ''} onChange={(e) => handleParamChange(condition.type.includes('CARD_ID') ? 'cardId' : 'flagName', e.target.value)} /></div>
                        <label><input type="checkbox" checked={!!condition.params?.mustBeAbsent} onChange={(e) => handleParamChange('mustBeAbsent', e.target.checked)} /> Must be absent</label>
                    </>
                );
            case 'TARGET_HAS_TYPE':
            case 'SOURCE_HAS_TYPE':
                return (
                    <>
                        <div className="flex-row"><label>Type Name:</label><input type="text" value={condition.params?.typeName || ''} onChange={(e) => handleParamChange('typeName', e.target.value)} /></div>
                        <label><input type="checkbox" checked={!!condition.params?.mustBeAbsent} onChange={(e) => handleParamChange('mustBeAbsent', e.target.checked)} /> Must be absent</label>
                    </>
                );
            case 'VALUE_COMPARISON':
                return (
                    <div className="flex-col">
                        <ValueInput value={condition.params?.sourceValue || 0} onChange={val => handleParamChange('sourceValue', val)} label="Source Value" />
                        <OptionSelector value={condition.params?.operator} options={["GREATER_THAN", "LESS_THAN", "EQUALS"]} onChange={(e) => handleParamChange('operator', e.target.value)} />
                        <ValueInput value={condition.params?.targetValue || 0} onChange={val => handleParamChange('targetValue', val)} label="Target Value" />
                    </div>
                );
            default: return null;
        }
    };

    return (
        <div className="condition-block">
            <div className="condition-controls">
                <OptionSelector value={condition.type} options={conditionTypes} onChange={handleTypeChange} />
                <button type="button" onClick={onRemove} className="remove-button small-button">Remove</button>
            </div>
            <div className="condition-params">
                {(condition.type === 'ALL_OF' || condition.type === 'ANY_OF') ?
                    (
                        <div className="condition-builder">
                            {condition.conditions?.map((sub, i) => (
                                <ConditionBuilder key={i} condition={sub} onChange={newSub => handleSubConditionChange(i, newSub)} onRemove={() => handleRemoveSubCondition(i)} />
                            ))}
                            <button type="button" onClick={handleAddSubCondition} className="admin-button secondary small-button">Add Sub-Condition</button>
                        </div>
                    ) : renderParams()
                }
            </div>
        </div>
    );
};

const ActionParamsBuilder = ({ actionType, params, onChange }) => {
    const { targetSelectors, statTypes } = useEffectOptions();

    const handleParamChange = (param, value) => {
        onChange(produce(params, draft => {
            draft[param] = value;
        }));
    };

    if (!actionType) return <p>Select an action type.</p>;

    return (
        <div className="flex-col action-params-builder effect-group">
            <h4>Action Parameters</h4>
            <div className="flex-row">
                <label>Targets:</label>
                <OptionSelector value={params?.targets} options={targetSelectors} onChange={(e) => handleParamChange('targets', e.target.value)} />
            </div>
            {['DEAL_DAMAGE', 'HEAL_TARGET'].includes(actionType) && (
                <ValueInput value={params?.amount} onChange={(val) => handleParamChange('amount', val)} label="Amount" />
            )}
            {['BUFF_STAT', 'DEBUFF_STAT'].includes(actionType) && (
                <>
                    <div className="flex-row"><label>Stat:</label><OptionSelector value={params?.stat} options={statTypes} onChange={(e) => handleParamChange('stat', e.target.value)} /></div>
                    <ValueInput value={params?.amount} onChange={(val) => handleParamChange('amount', val)} label="Amount" />
                    <label><input type="checkbox" checked={!!params?.isPermanent} onChange={(e) => handleParamChange('isPermanent', e.target.checked)} /> Permanent</label>
                </>
            )}
            {actionType === 'SET_STAT' && (
                <>
                    <div className="flex-row"><label>Stat:</label><OptionSelector value={params?.stat} options={statTypes} onChange={(e) => handleParamChange('stat', e.target.value)} /></div>
                    <ValueInput value={params?.value} onChange={(val) => handleParamChange('value', val)} label="Value" />
                </>
            )}
            {actionType === 'APPLY_FLAG' && (
                <>
                    <div className="flex-row"><label>Flag Name:</label><input type="text" value={params?.flagName || ''} onChange={(e) => handleParamChange('flagName', e.target.value)} /></div>
                    <div className="flex-row"><label>Value (string/bool/num):</label><input type="text" value={params?.value === undefined ? '' : String(params.value)} onChange={(e) => handleParamChange('value', e.target.value)} /></div>
                    <div className="flex-row"><label>Duration:</label><OptionSelector value={params?.duration} options={["PERMANENT", "TURN"]} onChange={(e) => handleParamChange('duration', e.target.value)} /></div>
                </>
            )}
            {actionType === 'TRANSFORM_CARD' && (
                <div className="flex-row"><label>New Card ID:</label><input type="text" value={params?.newCardId || ''} onChange={(e) => handleParamChange('newCardId', e.target.value)} /></div>
            )}
            {actionType === 'SCHEDULE_ACTION' && (
                <>
                    <div className="flex-row"><label>Delay (Turns):</label><input type="number" value={params?.delayInTurns || 1} onChange={(e) => handleParamChange('delayInTurns', parseInt(e.target.value, 10))} /></div>
                    <p>Scheduled Effect (JSON):</p>
                    <textarea style={{ minHeight: '80px' }} value={JSON.stringify(params?.scheduledEffect || {}, null, 2)} onChange={(e) => handleParamChange('scheduledEffect', JSON.parse(e.target.value))} />
                </>
            )}
        </div>
    );
};

const SingleEffectEditor = ({ effect, onChange, onRemove }) => {
    const { triggers, actions } = useEffectOptions();

    const handleFieldChange = (field, value) => {
        onChange(produce(effect, draft => {
            draft[field] = value;
            // When changing action, reset params
            if (field === 'action') draft.params = { targets: "SELF" };
        }));
    };

    const handleConditionChange = (newCondition) => {
        onChange(produce(effect, draft => {
            draft.condition = newCondition;
        }));
    };

    const handleAddCondition = () => handleConditionChange(defaultCondition());
    const handleRemoveCondition = () => handleConditionChange(null);

    const handleParamsChange = (newParams) => {
        onChange(produce(effect, draft => {
            draft.params = newParams;
        }));
    };

    return (
        <div className="effect-block">
            <div className="effect-header">
                <h4>Effect Editor</h4>
                <button type="button" onClick={onRemove} className="remove-button">Remove Effect</button>
            </div>
            <div className="effect-body">
                <div className="effect-group">
                    <label>Trigger</label>
                    <OptionSelector value={effect.trigger} options={triggers} onChange={(e) => handleFieldChange('trigger', e.target.value)} />
                    {effect.trigger === 'ACTIVATED' && (
                        <div className="activated-ability-details">
                            <input type="number" placeholder="Ability Index" value={effect.abilityOptionIndex || ''} onChange={e => handleFieldChange('abilityOptionIndex', parseInt(e.target.value, 10))} />
                            <input type="text" placeholder="Ability Name" value={effect.name || ''} onChange={e => handleFieldChange('name', e.target.value)} />
                            <input type="text" placeholder="Description" value={effect.description || ''} onChange={e => handleFieldChange('description', e.target.value)} />
                            <OptionSelector value={effect.requiresTarget} options={["NONE", "ANY_FIELD_CARD", "OPPONENT_FIELD_CARD", "OWN_FIELD_CARD"]} onChange={e => handleFieldChange('requiresTarget', e.target.value)} />
                        </div>
                    )}
                </div>

                <div className="effect-group">
                    <label>Condition</label>
                    {effect.condition ? (
                        <ConditionBuilder condition={effect.condition} onChange={handleConditionChange} onRemove={handleRemoveCondition} />
                    ) : (
                        <button type="button" onClick={handleAddCondition} className="admin-button secondary small-button">Add Condition</button>
                    )}
                </div>

                <div className="effect-group">
                    <label>Action</label>
                    <OptionSelector value={effect.action} options={actions} onChange={(e) => handleFieldChange('action', e.target.value)} />
                </div>

                <ActionParamsBuilder
                    actionType={effect.action}
                    params={effect.params}
                    onChange={handleParamsChange}
                />

            </div>
        </div>
    );
};


const EffectOptionsProvider = ({ children }) => {
    const [options, setOptions] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchOptions = async () => {
            try {
                const data = await getEffectOptions();
                setOptions(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };
        fetchOptions();
    }, []);

    if (loading) return <p>Loading effect options...</p>;
    if (error) return <p style={{ color: 'red' }}>Error loading options: {error}</p>;

    return (
        <EffectOptionsContext.Provider value={options}>
            {children}
        </EffectOptionsContext.Provider>
    );
};

const EffectBuilder = ({ effects, onChange }) => {

    const handleAddEffect = () => {
        onChange([...effects, defaultEffect()]);
    };

    const handleRemoveEffect = (index) => {
        const newEffects = [...effects];
        newEffects.splice(index, 1);
        onChange(newEffects);
    };

    const handleEffectChange = (index, updatedEffect) => {
        const newEffects = [...effects];
        newEffects[index] = updatedEffect;
        onChange(newEffects);
    };

    return (
        <EffectOptionsProvider>
            <div className="effect-builder-wrapper">
                <h3>Effect Configuration</h3>
                {effects.map((effect, index) => (
                    <SingleEffectEditor
                        key={index}
                        effect={effect}
                        onChange={(updatedEffect) => handleEffectChange(index, updatedEffect)}
                        onRemove={() => handleRemoveEffect(index)}
                    />
                ))}
                <div className="effect-builder-controls">
                    <button type="button" onClick={handleAddEffect} className="admin-button secondary">Add Effect</button>
                </div>
            </div>
        </EffectOptionsProvider>
    );
};

export default EffectBuilder;