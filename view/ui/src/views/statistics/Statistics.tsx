import { ReactElement, useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts';
import { doQuery, RowResult } from "../../api";
import { useEntityDescription } from "../../hooks/useEntityDescriptions";
import { useUrlData } from "../../hooks/useUrlData";
import { Attribute } from "../../model/attribute";
import './Statistics.scss';

export const Statistics = (): ReactElement => {
  const entityDescriptions = useEntityDescription();
  const [descriptionString, setDescriptionString] = useUrlData<Params>()('entityDescription');
  const [attributeString, setAttributeString] = useUrlData<Params>()('attribute');
  const [searchResults, setSearchResults] = useState<RowResult[]>([]);

  const description = entityDescriptions.find(ed => ed.uri === descriptionString);
  const possibleAttributes = description === undefined ? [] : description.attributes;
  const attribute = description === undefined ? undefined : description.attributes.find(attr => attr.uri === attributeString);

  const data = attribute === undefined ? [] : createBarChartData(attribute, searchResults);

  useEffect(() => {
    let canceled = false;

    if (description !== undefined && attribute !== undefined) {
      doQuery(description, "", []).then(entities => {
        if (!canceled) setSearchResults(entities.rows);
      });
    }

    return () => { canceled = true; }
  }, [description, attribute])

  return <div className="statistics-main">
    <select className="simple-select" onChange={(e) => setDescriptionString(e.target.value)} value={description === undefined ? "" : description.uri}>
      <option></option>
      {
        entityDescriptions.map(ed =>
          <option key={ed.uri} value={ed.uri}>
            {ed.name}
          </option>

        )
      }
    </select>
    <select disabled={possibleAttributes.length === 0} onChange={(e) => setAttributeString(e.target.value)} value={attribute === undefined ? "" : attribute.uri}>
      <option></option>
      {
        possibleAttributes.map(attr =>
          <option key={attr.uri} value={attr.uri}>
            {attr.label}
          </option>
        )
      }
    </select>

    <BarChart
      width={900}
      height={500}
      data={data}
    >
      <Tooltip />
      <CartesianGrid vertical={false} strokeDasharray="3 3" />
      <Bar fill="rgb(206, 196, 171)" dataKey="value" />
      <XAxis dataKey="name" />
      <YAxis />

    </BarChart>
  </div>
}

export interface Params {
  entityDescription: string;
  attribute: string;
}

const createBarChartData = (attribute: Attribute, searchResults: RowResult[]): { name: string, value: number }[] => {
  const aggregated: { [key: string]: number } = {};
  let noValues = 0;

  for (const row of searchResults) {
    const value = row.data[attribute.uri];
    if (!value) {
      noValues++;
    } else {
      aggregated[value] = aggregated[value] ? aggregated[value] + 1 : 1;
    }
  }

  return Object.keys(aggregated).map(name => ({ name, value: aggregated[name] })).concat([{ name: "Kein Wert", value: noValues }]);
}
